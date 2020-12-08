package ch.uzh.ifi.seal.soprafs20.service;

import ch.uzh.ifi.seal.soprafs20.GameLogic.APIResponse;
import ch.uzh.ifi.seal.soprafs20.GameLogic.NLP;
import ch.uzh.ifi.seal.soprafs20.GameLogic.WordReader;
import ch.uzh.ifi.seal.soprafs20.GameLogic.GameState;
import ch.uzh.ifi.seal.soprafs20.entity.Clue;
import ch.uzh.ifi.seal.soprafs20.entity.Game;
import ch.uzh.ifi.seal.soprafs20.entity.InternalTimer;
import ch.uzh.ifi.seal.soprafs20.entity.Lobby;
import ch.uzh.ifi.seal.soprafs20.entity.LobbyScore;
import ch.uzh.ifi.seal.soprafs20.entity.Player;
import ch.uzh.ifi.seal.soprafs20.entity.User;
import ch.uzh.ifi.seal.soprafs20.exceptions.ConflictException;
import ch.uzh.ifi.seal.soprafs20.exceptions.NotFoundException;
import ch.uzh.ifi.seal.soprafs20.exceptions.UnauthorizedException;
import ch.uzh.ifi.seal.soprafs20.repository.ClueRepository;
import ch.uzh.ifi.seal.soprafs20.repository.GameRepository;
import ch.uzh.ifi.seal.soprafs20.repository.LobbyRepository;
import ch.uzh.ifi.seal.soprafs20.repository.LobbyScoreRepository;
import ch.uzh.ifi.seal.soprafs20.repository.PlayerRepository;
import ch.uzh.ifi.seal.soprafs20.repository.UserRepository;
import ch.uzh.ifi.seal.soprafs20.rest.dto.CluePutDTO;
import ch.uzh.ifi.seal.soprafs20.rest.dto.GamePostDTO;
import ch.uzh.ifi.seal.soprafs20.rest.dto.MessagePutDTO;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;

/**
 * Handles the game logic.
 */
@Service
@Transactional
public class GameService {

    /**
     * The game repository.
     */
    private final GameRepository gameRepository;

    /**
     * The lobby repository.
     */
    private final LobbyRepository lobbyRepository;

    /**
     * The user repository.
     */
    private final UserRepository userRepository;

    /**
     * The clue repository.
     */
    private final ClueRepository clueRepository;

    /**
     * The lobby score repository.
     */
    private final LobbyScoreRepository lobbyScoreRepository;

    /**
     * The player repository.
     */
    private final PlayerRepository playerRepository;

    /**
     * Time to pick a word.
     */
    private static final int PICK_WORD_TIME = 10;

    /**
     * Time to enter a clue.
     */
    private static final int ENTER_CLUES_TIME = 30;

    /**
     * Time to vote.
     */
    private static final int VOTE_TIME = 15;

    /**
     * Time to guess.
     */
    private static final int GUESS_TIME = 30;

    /**
     * Time for transition.
     */
    private static final int TRANSITION_TIME = 5;

    /**
     * End time.
     */
    private static final int END_TIME = 10;

    /**
     * The timer schedule period.
     */
    private static final int SCHEDULE_PERIOD = 1000;

    /**
     * Amount of players for special game rules.
     */
    private static final int SPECIAL_GAME_AMOUNT_PLAYERS = 3;

    /**
     * Point deduction for incorrect guess.
     */
    private static final int INCORRECT_GUESS_DEDUCTION = -15;

    /**
     * Amount of random words that are picked.
     */
    private static final int RAND_WORDS = 13;

    /**
     * A random number.
     */
    private static final Random RAND = new Random();

    /**
     * Responsible for checking validity of clues.
     */
    private static final NLP NLP = new NLP();

    /**
     * Constructs an instance of this class.
     *
     * @param gameRepository       repository of stored games
     * @param lobbyRepository      repository of stored lobbies
     * @param userRepository       repository of stored users
     * @param lobbyScoreRepository repository of stored lobby scores
     * @param clueRepository       repository of stored clues
     * @param playerRepository     repository of stored players
     */
    @Autowired
    public GameService(final GameRepository gameRepository,
                       final LobbyRepository lobbyRepository,
                       final UserRepository userRepository,
                       final LobbyScoreRepository lobbyScoreRepository,
                       final ClueRepository clueRepository,
                       final PlayerRepository playerRepository) {
        this.gameRepository = gameRepository;
        this.lobbyRepository = lobbyRepository;
        this.userRepository = userRepository;
        this.clueRepository = clueRepository;
        this.lobbyScoreRepository = lobbyScoreRepository;
        this.playerRepository = playerRepository;
    }

    /**
     * Gets the game by id.
     *
     * @param id the id of the game.
     * @return the game by id.
     */
    public Game getGame(final Long id) {
        Game game;
        Optional<Game> optionalGame = gameRepository.findById(id);
        if (optionalGame.isPresent()) {
            game = optionalGame.get();
            return game;
        } else {
            throw new NotFoundException("Could not find game!");
        }
    }

    /**
     * Gets the maximal time for the current state of the game.
     *
     * @param game the game.
     * @return the maximal time in seconds.
     */
    public int getMaxTime(final Game game) {
        GameState gameState = game.getGameState();
        if (gameState.equals(GameState.END_GAME_STATE)) {
            return END_TIME;
        }
        else if (gameState.equals(GameState.PICK_WORD_STATE)) {
            return PICK_WORD_TIME;
        }
        else if (gameState.equals(GameState.TRANSITION_STATE)) {
            return TRANSITION_TIME;
        }
        else if (gameState.equals(GameState.ENTER_CLUES_STATE)) {
            return ENTER_CLUES_TIME;
        }
        else if (gameState.equals(GameState.VOTE_ON_CLUES_STATE)) {
            return VOTE_TIME;
        }
        return GUESS_TIME;
    }

    /**
     * Creates new {@code Game} instance, sets current guesser
     * and chooses first word.
     *
     * @param lobby       the {@code Lobby} for which the game is created.
     * @param gamePostDTO the game post dto.
     * @return the created game.
     */
    public Game createGame(final Lobby lobby,
                           final GamePostDTO gamePostDTO) {
        if (!lobby.getHostToken().equals(gamePostDTO.getHostToken())) {
            throw new UnauthorizedException(
                    "You are not allowed to start the game.");
        }
        if (lobby.isGameStarted()) {
            throw new ConflictException("Game has already started!");
        }
        // set lobby status to started
        lobby.setGameIsStarted(true);

        // init new game
        Game newGame = new Game();
        newGame.setLobbyId(lobby.getLobbyId());
        newGame.setGameState(GameState.PICK_WORD_STATE);
        newGame.setLobbyName(lobby.getLobbyName());
        newGame.setRounds(lobby.getRounds());

        for (Player player : lobby.getPlayersInLobby()) {
            newGame.addPlayer(player);
        }

        // if there are only 3 players, the special rule set has to be applied
        int players = lobby.getCurrentNumBots()
                + lobby.getCurrentNumPlayers();
        newGame.setSpecialGame(players == SPECIAL_GAME_AMOUNT_PLAYERS);

        // assign first guesser
        Player currentGuesser = newGame.getPlayers()
                .get(RAND.nextInt(newGame.getPlayers().size()));
        newGame.setCurrentGuesser(currentGuesser);

        // set round count to 1
        newGame.setRoundsPlayed(1);
        setStartTime(TimeUnit.MILLISECONDS.toSeconds(
                System.currentTimeMillis()),
                newGame);

        // select random words from words.txt
        WordReader reader = new WordReader();
        newGame.setWords(reader.getRandomWords(RAND_WORDS));

        newGame = gameRepository.save(newGame);
        gameRepository.flush();
        return newGame;
    }

    /**
     * Sends a clue to a player.
     *
     * @param game       the game.
     * @param player     the player.
     * @param cluePutDTO the clue to be sent.
     * @return whether the clue was successfully sent.
     */
    public boolean sendClue(final Game game, final Player player,
                            final CluePutDTO cluePutDTO) {
        // check if game is in a valid state to accept clues
        if (!game.getGameState().equals(GameState.ENTER_CLUES_STATE)) {
            throw new UnauthorizedException(
                    "Clues are not accepted in current state!");
        }
        if (!game.getPlayers().contains(player)
                || player.isClueIsSent()
                || game.getCurrentGuesser().equals(player)
                || (!player.getToken().equals(
                        cluePutDTO.getPlayerToken()))) {
            throw new UnauthorizedException(
                    "This player is not allowed to send a clue!");
        }
        // check if player is allowed to send a clue
        if (!game.isSpecialGame()) {
            Clue clue = new Clue();
            clue.setPlayerId(player.getId());
            clue.setActualClue(cluePutDTO.getMessage());
            clue.setTimeNeeded(
                    ENTER_CLUES_TIME
                    - (TimeUnit.MILLISECONDS.toSeconds(
                            System.currentTimeMillis())
                    - game.getStartTimeSeconds()));
            player.addClue(clue);
            player.setClueIsSent(true);
            // if the same clue is sent twice, it is removed once
            addClue(clue, game);
            clueRepository.saveAndFlush(clue);
            gameRepository.saveAndFlush(game);
        }
        else {
            sendClueSpecial(game, player, cluePutDTO);
        }
        int counter = 0;
        for (Player playerInGame : game.getPlayers()) {
            if (playerInGame.isClueIsSent()) {
                counter++;
            }
        }
        if (allSent(game, counter)) {
            generateCluesForBots(game);
            checkClues(game);
            gameRepository.saveAndFlush(game);
            return true;
        }
        return false;
    }


    /**
     * Overloaded sendClue method for the case that the timer runs out
     * and not every player sent a clue.
     *
     * @param game the game.
     */
    public void sendClue(final Game game) {
        // if a user did not send a clue, fill his clue with empty string
        for (Player p : game.getPlayers()) {
            if (!game.getCurrentGuesser().equals(p)) {
                p.setClueIsSent(true);
            }
        }
        if (game.getEnteredClues().isEmpty()) {
            checkClues(game);
        }
        generateCluesForBots(game);
    }

    /**
     * Picks a word and enters the clue game state.
     *
     * @param token the token.
     * @param game  the game.
     * @return whether the word was saved successfully.
     */
    public boolean pickWord(final String token, final Game game) {
        if (!game.getCurrentGuesser().getToken().equals(token)) {
            throw new UnauthorizedException(
                    "This player is not allowed to pick a word!");
        }
        game.setCurrentWord(chooseWordAtRandom(game.getWords()));
        game.setGameState(GameState.ENTER_CLUES_STATE);
        gameRepository.saveAndFlush(game);
        return true;
    }

    /**
     * Overloaded pickWord method for the case that the timer runs out
     * and the guesser did not send a guess.
     *
     * @param game the game.
     */
    public void pickWord(final Game game) {
        game.setCurrentWord(chooseWordAtRandom(game.getWords()));
    }


    /**
     * Sends clues in case of a special game (i.e. 3 players)
     *
     * @param game       the game.
     * @param player     the player.
     * @param cluePutDTO the clue to send.
     */
    private void sendClueSpecial(final Game game,
                                 final Player player,
                                 final CluePutDTO cluePutDTO) {
        Clue firstClue = new Clue();
        firstClue.setPlayerId(player.getId());
        firstClue.setActualClue(cluePutDTO.getMessage());
        firstClue.setTimeNeeded(
                ENTER_CLUES_TIME
                - (TimeUnit.MILLISECONDS.toSeconds(
                        System.currentTimeMillis())
                        - game.getStartTimeSeconds()));
        player.addClue(firstClue);

        Clue secondClue = new Clue();
        secondClue.setPlayerId(player.getId());
        secondClue.setTimeNeeded(
                ENTER_CLUES_TIME
                - (TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis())
                - game.getStartTimeSeconds()));
        if (cluePutDTO.getMessage2() != null) {
            secondClue.setActualClue(cluePutDTO.getMessage2());
            player.addClue(secondClue);
        }
        else {
            secondClue.setActualClue("");
        }
        addClue(firstClue, game);
        addClue(secondClue, game);
        player.setClueIsSent(true);

        gameRepository.saveAndFlush(game);
        clueRepository.saveAndFlush(firstClue);
        clueRepository.saveAndFlush(secondClue);
    }

    /**
     * Submits a guess.
     *
     * @param game          the game.
     * @param messagePutDTO the guessed message.
     * @param time          the time.
     */
    public void submitGuess(final Game game,
                            final MessagePutDTO messagePutDTO,
                            final long time) {
        if (!game.getCurrentGuesser()
                .getToken()
                .equals(messagePutDTO.getPlayerToken())) {
            throw new UnauthorizedException(
                    "User is not allowed to submit a guess!");
        }
        if (game.getCurrentGuesser().isGuessIsSent()) {
            throw new UnauthorizedException(
                    "This player already submitted his guess!");
        }
        if (!game.getGameState().equals(GameState.ENTER_GUESS_STATE)) {
            throw new UnauthorizedException(
                    "Can't submit guess in current state!");
        }

        game.getCurrentGuesser().setGuessIsSent(true);
        game.setGuessCorrect(messagePutDTO.getMessage()
                .equalsIgnoreCase(game.getCurrentWord()));
        game.setCurrentGuess(messagePutDTO.getMessage());
        guesserScore(game, time);
        gameRepository.saveAndFlush(game);
    }

    private void guesserScore(final Game game, final long time) {
        final int timeFactor = 5;
        final int invalidGuessDeduction = -30;
        int pastScore = game.getCurrentGuesser().getScore();
        int score = 0;

        if (game.isGuessCorrect()) {
            score = (int) ((GUESS_TIME - time) * timeFactor);

            //In case of special game (only three players), double the reward
            if (game.isSpecialGame()) {
                score = score * 2;
            }
            game.setOverallScore(game.getOverallScore() + score);
            game.getCurrentGuesser().setScore(pastScore + score);
        }
        else {
            score = -invalidGuessDeduction;
            //In case of special game (only three players), double the deduction
            if (game.isSpecialGame()) {
                score = score * 2;
            }
            //score
            game.getCurrentGuesser()
                    .setScore(Math.max(pastScore + score, 0));
            if (game.getCurrentGuesser().getScore() <= 0) {
                game.setOverallScore(
                        Math.max(game.getOverallScore() - pastScore, 0));
            } else {
                game.setOverallScore(
                        Math.max(game.getOverallScore() + score, 0));
            }
        }
    }

    /**
     * Starts a new round.
     *
     * @param game the game.
     */
    public void startNewRound(final Game game) {
        game.setRoundsPlayed(game.getRoundsPlayed() + 1);
        //determine the player who is next to guess
        int index = game.getPlayers().indexOf(game.getCurrentGuesser());
        Player currentGuesser = game.getPlayers().get((index + 1)
                % game.getPlayers().size());
        game.setCurrentGuesser(currentGuesser);
        //reset the other players
        game.getPlayers().forEach(p -> {
            p.setClueIsSent(false);
            p.setVoted(false);
            p.getClues().clear();
        });

        game.getCurrentGuesser().setGuessIsSent(false);
        game.getEnteredClues().clear();
        game.getInvalidClues().clear();
        game.setGuessCorrect(false);
        game.setCurrentGuess("");
        gameRepository.saveAndFlush(game);
    }

    /**
     * Checks the clues for validity.
     *
     * @param game the game.
     */
    public void checkClues(final Game game) {
        List<Clue> invalidClues = new ArrayList<>();
        for (Clue clue : game.getEnteredClues()) {
            if (!NLP.checkClue(clue.getActualClue(),
                    game.getCurrentWord())) {
                clue.setPlayerId(-1L);
                invalidClues.add(clue);
            }
        }
        game.getEnteredClues().removeAll(invalidClues);
        game.addInvalidClues(invalidClues);
        gameRepository.saveAndFlush(game);
    }

    /**
     * Checks whether all clues or votes have been sent.
     *
     * @param game    the game.
     * @param counter players
     * @return whether all clues/votes of each player are received.
     */
    public boolean allSent(final Game game, final int counter) {
        return counter == game.getPlayers().size() - 1;
    }

    /**
     * Sets the start time.
     *
     * @param time the start time.
     * @param game the game.
     */
    public void setStartTime(final long time, final Game game) {
        game.setStartTimeSeconds(time);
        gameRepository.saveAndFlush(game);
    }


    /**
     * Gets a word at random.
     *
     * @param words the list of words.
     * @return random word from the list.
     */
    public String chooseWordAtRandom(final List<String> words) {
        String currentWord = words.get(
                RAND.nextInt(words.size()));
        words.remove(currentWord);
        return currentWord;
    }

    /**
     * Updates the scores of a game.
     *
     * @param game the game.
     */
    public void updateScores(Game game) {
        game = getUpdatedGame(game);
        int submittedClues = 0;
        for (Clue clue : game.getEnteredClues()) {
            if ((clue.getPlayerId() != 0L)) {
                submittedClues++;
            }
        }
        for (Player player : game.getPlayers()) {
            // in case of 3-player-logic, the size of clues is 2,
            // otherwise 1 (or 0, if player did not send any clues)
            for (int i = 0; i < player.getClues().size(); i++) {
                if (game.getEnteredClues().contains(player.getClue(i))) {
                    int newScore = 0;
                    if (!game.isSpecialGame() && game.isGuessCorrect()) {
                        newScore = (int) (player.getClue(i).getTimeNeeded()
                                * ((game.getPlayers().size() - submittedClues)));
                    }
                    if (!game.isSpecialGame()
                            && !game.isGuessCorrect()) {
                        newScore = INCORRECT_GUESS_DEDUCTION;
                    }

                    if (game.isSpecialGame() && game.isGuessCorrect()) {
                        newScore = (int) (player.getClue(i).getTimeNeeded()
                                * ((game.getPlayers().size() * 2 - submittedClues)));
                    }
                    else {
                        if (game.isGuessCorrect()) {
                            newScore =
                                    (int) (player.getClue(i).getTimeNeeded() *
                                    ((game.getPlayers().size() * 2 - submittedClues)));
                        }
                        else {
                            newScore = -INCORRECT_GUESS_DEDUCTION * 2;
                        }
                    }
                    player.setScore(Math.max(player.getScore() + newScore, 0));
                    if (player.getScore() <= 0) {
                        game.setOverallScore(
                                Math.max(game.getOverallScore() - player.getScore(), 0));
                    }
                    else {
                        game.setOverallScore(
                                Math.max(game.getOverallScore() + newScore, 0));
                    }
                }
            }
        }
    }

    /**
     * Updates the user database.
     *
     * @param game the game.
     */
    void updateUserDatabase(final Game game) {
        for (Player player : game.getPlayers()) {
            Optional<User> optionalUser = userRepository.findById(player.getId());
            if (optionalUser.isPresent()) {
                User user = optionalUser.get();
                user.setScore(user.getScore() + player.getScore());
                userRepository.saveAndFlush(user);
            }
        }
    }

    /**
     * Central timer logic for each game. Sets timer for each state.
     * If state is complete before the timer ends,
     * the game transitions into the next state with a new timer.
     * Timer also takes care of all the logic set up
     * for the next state if no user input was entered
     *
     * @param g the game instance.
     */
    public void timer(final Game g) {
        final Game[] game = {g};
        TimerTask timerTask = new TimerTask() {
            @Override
            public void run() {
                game[0] = getUpdatedGame(game[0]);
                game[0].setTime(
                        TimeUnit.MILLISECONDS.toSeconds(
                                System.currentTimeMillis())
                                - game[0].getStartTimeSeconds());

                // PickwordState
                if (game[0].getTime() >= PICK_WORD_TIME
                        && game[0].getRoundsPlayed() <= game[0].getRounds()
                        && !getCancel(game[0])
                        && game[0].getGameState().equals(
                                GameState.PICK_WORD_STATE)) {
                    game[0].getTimer().cancel();
                    game[0].getTimer().purge();
                    pickWord(game[0]);

                    game[0].setGameState(getNextState(game[0]));
                    game[0].setStartTimeSeconds(
                            TimeUnit.MILLISECONDS.toSeconds(
                                    System.currentTimeMillis()));

                    gameRepository.saveAndFlush(game[0]);
                } else if (game[0].getTime() >= ENTER_CLUES_TIME
                        && game[0].getRoundsPlayed() <= game[0].getRounds()
                        && !getCancel(game[0])
                        && game[0].getGameState().equals(
                                GameState.ENTER_CLUES_STATE)) {

                    // EnterCluesState
                    game[0].getTimer().cancel();
                    game[0].getTimer().purge();
                    sendClue(game[0]);
                    game[0].setGameState(getNextState(game[0]));
                    game[0].setStartTimeSeconds(
                            TimeUnit.MILLISECONDS.toSeconds(
                                    System.currentTimeMillis()));
                    gameRepository.saveAndFlush(game[0]);
                } else if (game[0].getTime() >= VOTE_TIME
                        && game[0].getRoundsPlayed() <= game[0].getRounds()
                        && !getCancel(game[0])
                        && game[0].getGameState().equals(
                                GameState.VOTE_ON_CLUES_STATE)) {

                    // VoteState
                    game[0].getTimer().cancel();
                    game[0].getTimer().purge();
                    vote(game[0]);
                    game[0].setGameState(getNextState(game[0]));
                    game[0].setStartTimeSeconds(
                            TimeUnit.MILLISECONDS.toSeconds(
                                    System.currentTimeMillis()));
                    gameRepository.saveAndFlush(game[0]);
                } else if (game[0].getTime() >= GUESS_TIME
                        && game[0].getRoundsPlayed() <= game[0].getRounds()
                        && !getCancel(game[0])
                        && game[0].getGameState()
                        .equals(GameState.ENTER_GUESS_STATE)) {

                    // GuessState
                    game[0].getTimer().cancel();
                    game[0].getTimer().purge();
                    game[0].setGuessCorrect(false);
                    game[0].setGameState(getNextState(game[0]));
                    updateScores(game[0]);
                    guesserScore(game[0], GUESS_TIME);

                    game[0].setStartTimeSeconds(
                            TimeUnit.MILLISECONDS.toSeconds(
                                    System.currentTimeMillis()));
                    gameRepository.saveAndFlush(game[0]);
                } else if (game[0].getTime() >= TRANSITION_TIME
                        && game[0].getRoundsPlayed() <= game[0].getRounds()
                        && !getCancel(game[0])
                        && game[0].getGameState().equals(
                                GameState.TRANSITION_STATE)) {

                    // TransitionState
                    game[0].getTimer().cancel();
                    game[0].getTimer().purge();
                    startNewRound(game[0]);
                    if (game[0].getRoundsPlayed() > game[0].getRounds()) {
                        game[0].setGameState(GameState.END_GAME_STATE);
                        game[0].setRoundsPlayed(game[0].getRounds());
                    } else {
                        game[0].setGameState(getNextState(game[0]));
                    }
                    game[0].setStartTimeSeconds(
                            TimeUnit.MILLISECONDS.toSeconds(
                                    System.currentTimeMillis()));
                    gameRepository.saveAndFlush(game[0]);
                } else if (game[0].getTime() >= END_TIME
                        && !getCancel(game[0])
                        && game[0].getGameState().equals(
                                GameState.END_GAME_STATE)) {

                    // EndGameState
                    game[0].getTimer().cancel();
                    game[0].getTimer().purge();
                    g.getTimer().cancel();
                    g.getTimer().purge();

                    updateUserDatabase(game[0]);
                    Lobby currentLobby = getUpdatedLobby(game[0].getLobbyId());
                    currentLobby.setGameIsStarted(false);
                    lobbyRepository.saveAndFlush(currentLobby);

                    LobbyScore lobbyScore = new LobbyScore();
                    lobbyScore.setLobbyName(game[0].getLobbyName());
                    lobbyScore.setScore(game[0].getOverallScore());
                    lobbyScore.setPlayersIdInLobby(game[0].getPlayers());
                    lobbyScore.setDate(new Date());
                    lobbyScoreRepository.saveAndFlush(lobbyScore);

                    for (Player p : game[0].getPlayers()) {
                        p.setScore(0);
                    }
                    playerRepository.saveAll(game[0].getPlayers());

                    game[0].setPlayers(null);
                    game[0].setCurrentGuesser(null);
                    gameRepository.saveAndFlush(game[0]);
                    gameRepository.delete(game[0]);
                    gameRepository.flush();
                } else if (getCancel(game[0])
                        && game[0].getRoundsPlayed() <= game[0].getRounds()
                        && !game[0].getGameState().equals(
                                GameState.END_GAME_STATE)) {

                    // Player input cancels timer
                    game[0].getTimer().cancel();
                    game[0] = getUpdatedGame(game[0]);
                    game[0].setStartTimeSeconds(
                            TimeUnit.MILLISECONDS.toSeconds(
                                    System.currentTimeMillis()));
                    game[0].getTimer().setCancel(false);
                    gameRepository.saveAndFlush(game[0]);
                }
            }
        };
        if (game[0].getRoundsPlayed() <= game[0].getRounds()) {
            game[0].getTimer().schedule(timerTask, 0, SCHEDULE_PERIOD);
        }
    }

    /**
     * Gets the lobby by id.
     *
     * @param lobbyId the lobby id.
     * @return the lobby.
     */
    public Lobby getUpdatedLobby(final Long lobbyId) {
        Optional<Lobby> currentLobby = lobbyRepository
                .findByLobbyId(lobbyId);
        if (currentLobby.isPresent()) {
            return currentLobby.get();
        }
        throw new NotFoundException(
                String.format("Lobby with ID %d not found", lobbyId));
    }

    /**
     * Gets whether the current timer is cancelled.
     *
     * @param game the game.
     * @return whether the timer is cancelled.
     */
    public boolean getCancel(final Game game) {
        Optional<Game> updated = gameRepository
                .findByLobbyId(game.getLobbyId());
        return updated.map(value -> value.getTimer()
                .isCancel()).orElse(false);
    }

    /**
     * Gets the game from the {@code gameRepository}.
     *
     * @param game the game.
     * @return the game.
     */
    public Game getUpdatedGame(final Game game) {
        Optional<Game> currentGame = gameRepository
                .findByLobbyId(game.getLobbyId());
        return currentGame.orElse(game);
    }

    /**
     * Gets the next game state.
     *
     * @param game the game.
     * @return the next state that the game will enter.
     */
    public GameState getNextState(Game game) {
        GameState nextGameState;
        GameState currentGameState = game.getGameState();
        switch (currentGameState) {
            case PICK_WORD_STATE:
                nextGameState = GameState.ENTER_CLUES_STATE;
                break;
            case ENTER_CLUES_STATE:
                nextGameState = GameState.VOTE_ON_CLUES_STATE;
                break;
            case VOTE_ON_CLUES_STATE:
                nextGameState = GameState.ENTER_GUESS_STATE;
                break;
            case ENTER_GUESS_STATE:
                nextGameState = GameState.TRANSITION_STATE;
                break;
            case TRANSITION_STATE:
                nextGameState = GameState.PICK_WORD_STATE;
                break;
            default:
                nextGameState = GameState.END_GAME_STATE;
                break;
        }
        return nextGameState;
    }

    /**
     * Sets the timer for a game.
     *
     * @param game the game.
     */
    public void setTimer(final Game game) {
        game.setTimer(new InternalTimer());
        game.getTimer().setCancel(false);
    }

    /**
     * Generates clues for bots.
     *
     * @param game the game.
     */
    public void generateCluesForBots(final Game game) {
        Lobby lobby;
        Optional<Lobby> foundLobby = lobbyRepository
                .findByLobbyId(game.getLobbyId());
        if (foundLobby.isPresent()) {
            lobby = foundLobby.get();
        }
        else {
            return;
        }
        String uri;
        // the api call is a bit different
        // if the current word consists of two separate words
        String[] split = game.getCurrentWord().split(" ");
        if (split.length == 1) {
            uri = String.format(
                    "https://api.datamuse.com/words?ml=%s",
                    split[0]);
        }
        else if (split.length == 2) {
            uri = String.format(
                    "https://api.datamuse.com/words?ml=%s+%s",
                    split[0], split[1]);
        }
        else {
            return;
        }
        RestTemplate restTemplate = new RestTemplate();
        String result = restTemplate.getForObject(uri, String.class);
        // in the case of a game with 3 players,
        // a bot submits two clues instead of one
        int amountOfClues = (game.isSpecialGame() ?
                lobby.getCurrentNumBots() * 2
                : lobby.getCurrentNumBots());
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            List<APIResponse> response = objectMapper
                    .readValue(result, new TypeReference<>() {
                    });
            Iterator<APIResponse> iterator = response.iterator();
            for (int i = 0; i < amountOfClues; i++) {
                while (iterator.hasNext()) {
                    APIResponse apiResponse = iterator.next();
                    String potentialClue = apiResponse.getWord();
                    if (NLP.checkClue(potentialClue, game.getCurrentWord())) {
                        Clue clueFromBot = new Clue();
                        clueFromBot.setPlayerId(0L);
                        clueFromBot.setActualClue(potentialClue);
                        if (!game.getEnteredClues().contains(clueFromBot)) {
                            game.getEnteredClues().add(clueFromBot);
                            clueRepository.saveAndFlush(clueFromBot);
                            break;
                        }
                    }
                }
            }
        }
        catch (JsonProcessingException ex) {
            ex.getMessage();
        }
    }

    /**
     * Checks if the player has already voted.
     * If not, it stores the invalid words of the player.
     * If all players have voted the invalid clues and the timer gets canceled.
     *
     * @param game         the game.
     * @param player       the player that votes.
     * @param invalidWords a list of invalid words.
     * @return whether all votes were successful.
     */
    public boolean vote(final Game game, final Player player, final List<String> invalidWords) {
        if (player.isVoted()) {
            throw new UnauthorizedException(
                    "This player already sent his votes!");
        }
        else {
            for (String s : invalidWords) {
                Clue clue = new Clue();
                clue.setPlayerId(player.getId());
                clue.setActualClue(s);
                game.addInvalidClue(clue);
            }
            player.setVoted(true);
        }
        int counter = 0;
        for (Player p : game.getPlayers()) {
            if (p.isVoted())
                counter++;
        }
        if (counter == game.getPlayers().size() - 1) {
            int ceil = (int) Math.ceil(
                    ((float) game.getPlayers().size() - 1) / 2);
            checkVotes(game, ceil);
            game.getTimer().setCancel(true);
            gameRepository.saveAndFlush(game);
        }
        return allSent(game, counter);
    }

    /**
     * Vote on the game.
     *
     * @param game the game.
     */
    public void vote(final Game game) {
        for (Player p : game.getPlayers()) {
            if (!game.getCurrentGuesser().equals(p) && !p.isVoted()) {
                p.setVoted(true);
            }
        }
        checkVotes(game, (int) Math.ceil(
                ((float) game.getPlayers().size() - 1) / 2));
        gameRepository.saveAndFlush(game);
    }

    /**
     * Checks the validity of the votes.
     *
     * @param game      the game.
     * @param threshold the threshold of maximal votes allowed.
     */
    public void checkVotes(final Game game, final int threshold) {
        // If there is only one real player and the rest are bots,
        // voting is not necessary since bots can not vote
        if (game.getPlayers().size() < 2) {
            return;
        }
        List<Clue> actualInvalidClues = new ArrayList<>();
        Iterator<Clue> iterator = game.getEnteredClues().iterator();
        while (iterator.hasNext()) {
            Clue clue = iterator.next();
            int occurrences =
                    Collections.frequency(game.getInvalidClues(), clue);
            if (occurrences >= threshold) {
                iterator.remove();
                if (!actualInvalidClues.contains(clue)) {
                    actualInvalidClues.add(clue);
                }
            }
        }
        // Iterate over invalidClues to preserve clues voted out from NLP
        iterator = game.getInvalidClues().iterator();
        while (iterator.hasNext()) {
            Clue invalidClue = iterator.next();
            if (invalidClue.getPlayerId().equals(-1L)
                    || invalidClue.getPlayerId().equals(0L)) {
                if (!actualInvalidClues.contains(invalidClue)) {
                    actualInvalidClues.add(invalidClue);
                }
            }
        }
        // Remove duplicates from list of invalid clues to return to client
        game.setInvalidClues(actualInvalidClues);
        gameRepository.saveAndFlush(game);
    }

    /**
     * Adds a clue to a game.
     *
     * @param clue the clue.
     * @param game the game.
     */
    public void addClue(final Clue clue, final Game game) {
        // If the same clue is sent twice, remove it from list of entered clues
        if (game.getEnteredClues().contains(clue)) {
            game.getEnteredClues().remove(clue);
            clue.setPlayerId(0L);
            if (!game.getInvalidClues().contains(clue)) {
                game.addInvalidClue(clue);
            }
        }
        else if (!game.getInvalidClues().contains(clue)) {
            // Only add the clue to list of entered clues
            // if the same clue wasn't sent before
            game.addClue(clue);
        }
        gameRepository.saveAndFlush(game);
    }
}
