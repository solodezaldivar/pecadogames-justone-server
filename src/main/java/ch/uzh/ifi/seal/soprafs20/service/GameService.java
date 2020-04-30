package ch.uzh.ifi.seal.soprafs20.service;

import ch.uzh.ifi.seal.soprafs20.GameLogic.NLP;
import ch.uzh.ifi.seal.soprafs20.GameLogic.WordReader;
import ch.uzh.ifi.seal.soprafs20.GameLogic.gameStates.GameState;
import ch.uzh.ifi.seal.soprafs20.entity.*;
import ch.uzh.ifi.seal.soprafs20.exceptions.ConflictException;
import ch.uzh.ifi.seal.soprafs20.exceptions.ForbiddenException;
import ch.uzh.ifi.seal.soprafs20.exceptions.NotFoundException;
import ch.uzh.ifi.seal.soprafs20.exceptions.UnauthorizedException;
import ch.uzh.ifi.seal.soprafs20.repository.GameRepository;
import ch.uzh.ifi.seal.soprafs20.rest.dto.GamePostDTO;
import ch.uzh.ifi.seal.soprafs20.rest.dto.MessagePutDTO;
import ch.uzh.ifi.seal.soprafs20.rest.dto.RequestPutDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.concurrent.TimeUnit;


/**
 * Game Service
 * This class is the "worker" and responsible for all functionality related to the Game
 * The result will be passed back to the caller.
 */
@Service
@Transactional
public class GameService extends Thread{
    private final GameRepository gameRepository;
    private final Logger log = LoggerFactory.getLogger(GameService.class);

    @Autowired
    public GameService(GameRepository gameRepository) {
        this.gameRepository = gameRepository;
    }

    public Game getGame(Long id) {
        Game game;
        Optional<Game> optionalGame = gameRepository.findById(id);
        if (optionalGame.isPresent()) {
            game = optionalGame.get();
            return game;
        }
        else {
            throw new NotFoundException("Could not find game!");
        }
    }

    /**
     * creates new Game instance, sets current guesser and chooses first word
     *
     * @param lobby
     * @param gamePostDTO
     * @return
     */
    public Game createGame(Lobby lobby, GamePostDTO gamePostDTO) {
        if (!lobby.getToken().equals(gamePostDTO.getHostToken())) {
            throw new UnauthorizedException("You are not allowed to start the game.");
        }
        if (lobby.isGameStarted()) {
            throw new ConflictException("Game has already started!");
        }
        //set lobby status to started
        lobby.setGameIsStarted(true);

        //init new game
        Game newGame = new Game();
        newGame.setLobbyId(lobby.getLobbyId());
        newGame.setGameState(GameState.PICKWORDSTATE);


        for (Player player : lobby.getPlayersInLobby()) {
            newGame.addPlayer(player);
        }

        //if there are only 3 players, the special rule set has to be applied
        newGame.setSpecialGame(newGame.getPlayers().size() == 3);

        //assign first guesser
        Random rand = new Random();
        Player currentGuesser = newGame.getPlayers().get(rand.nextInt(newGame.getPlayers().size()));
        newGame.setCurrentGuesser(currentGuesser);

        //set round count to 0
        newGame.setRoundsPlayed(0);

        //select 13 random words from words.txt
        WordReader reader = new WordReader();
        newGame.setWords(reader.getRandomWords(13));

        newGame = gameRepository.save(newGame);
        gameRepository.flush();
        return newGame;
    }

    /**
     * @param game
     * @param player
     * @param clue
     * @return game with updated clue list
     */
    public boolean sendClue(Game game, Player player, Clue clue){
        if(!game.getGameState().equals(GameState.ENTERCLUESSTATE))
            throw new ForbiddenException("Clues are not accepted in current state!");

//        if(!game.getTimer().isRunning())
//            throw new ForbiddenException("Time ran out!");

        if(!game.getPlayers().contains(player) || player.isClueIsSent() || game.getCurrentGuesser().equals(player)){
            throw new ForbiddenException("This player is not allowed to send clue!");
        }

//        if(!game.getTimer().isRunning()){
//            throw new ForbiddenException("Time ran out!");
//        }
        if (!game.isSpecialGame()) {
            game.addClue(clue);
            player.setClueIsSent(true);
        }
        else {
            sendClueSpecial(game, player, clue);//handle double clue input from player
        }
        int counter = 0;
        for (Player playerInGame : game.getPlayers()){
            if (playerInGame.isClueIsSent()){
                counter++;
            }
        }
        if(allCluesSent(game, counter)) {
            game.setGameState(GameState.VOTEONCLUESSTATE);
            //game.getTimer().setCancel(true);
            checkClues(game);
            return true;
        }
        return false;
    }

    public void pickWord(String token, Game game) {
        if (!game.getCurrentGuesser().getToken().equals(token)) {
            throw new UnauthorizedException("This player is not allowed to pick a word!");
        }
        game.setCurrentWord(chooseWordAtRandom(game.getWords()));
        game.setGameState(GameState.ENTERCLUESSTATE);
        game.setRoundsPlayed(game.getRoundsPlayed() + 1);
        setStartTime(TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis()), game);
    }


    /**
     * method adds clue and token of player to clues. When a player enters their second clue, their token is replaced with it
     *
     * @param game
     * @param player
     * @param clue
     */
    private void sendClueSpecial(Game game, Player player, Clue clue) {
        Clue temporaryClue = new Clue();
        temporaryClue.setPlayerId(player.getId());
        temporaryClue.setActualClue(player.getToken());
        if (!game.getEnteredClues().isEmpty()) {
            if(game.getEnteredClues().removeIf(clue1 -> clue1.getActualClue().equals(player.getToken()))) {
                game.addClue(clue);
                player.setClueIsSent(true);
                return;
            }
        }
        game.addClue(temporaryClue);
    }

    public void submitGuess(Game game, MessagePutDTO messagePutDTO, long currentTimeSeconds) {
        if (!game.getCurrentGuesser().getToken().equals(messagePutDTO.getPlayerToken())) {
            throw new UnauthorizedException("User is not allowed to submit a guess!");
        }
        if(!game.getGameState().equals(GameState.ENTERGUESSSTATE)) {
            throw new ForbiddenException("Can't submit guess in current state!");
        }
        if (currentTimeSeconds - game.getStartTimeSeconds() > 60) {
            throw new ForbiddenException("Time ran out!");
        }
        game.setGuessCorrect(messagePutDTO.getMessage().toLowerCase().equals(game.getCurrentWord().toLowerCase()));
        game.setGameState(GameState.TRANSITIONSTATE);
    }

    public void startNewRound(Game game, RequestPutDTO requestPutDTO) {
        if (!game.getCurrentGuesser().getToken().equals(requestPutDTO.getToken())) {
            throw new ForbiddenException("User is not allowed to start a new round!");
        }
        game.setRoundsPlayed(game.getRoundsPlayed() + 1);

        int index = game.getPlayers().indexOf(game.getCurrentGuesser());
        Player currentGuesser = game.getPlayers().get((index + 1) % game.getPlayers().size());
        game.setCurrentGuesser(currentGuesser);

        game.setGameState(GameState.PICKWORDSTATE);

        //ToDo: Update scores of player and overall score
    }

    public void checkClues(Game game) {
        NLP nlp = new NLP();
        Iterator<Clue> iterator = game.getEnteredClues().iterator();
        while(iterator.hasNext()) {
            if(!nlp.checkClue(iterator.next().getActualClue(), game.getCurrentWord())) {
                iterator.remove();
            }
        }
    }

    public boolean allCluesSent(Game game, int counter) {
        if(game.isSpecialGame()) {
            return counter == (game.getPlayers().size() - 1) * 2;
        }
        return counter == game.getPlayers().size() - 1;
    }

    public void setStartTime(long time, Game game) {
        game.setStartTimeSeconds(time);
    }


    /**
     * Helper function that returns a random word from list and deletes it from list
     *
     * @param words
     * @return
     */
    public String chooseWordAtRandom(List<String> words) {
        Random random = new Random();
        String currentWord = words.get(random.nextInt(words.size()));
        words.remove(currentWord);
        return currentWord;
    }


    /**
     * Intern timer for server, if timer ends transition to next state
     *
     * @param g
     * @param gameState - state to which the game transitions if timer is finished
     */
    public void timer(Game g, GameState gameState, long startTime) {
        final int[] counter = {0};
        final Game[] game = {g};
        TimerTask timerTask = new TimerTask() {
            @Override
            public void run() {
                while (!getCancel(game[0])) {
                    game[0].setTime(TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis()) - startTime);
                    System.out.println(game[0].getTime() + " , first thread: " + game[0].getGameState());
                    if (game[0].getTime() >= 10) {
                        game[0].getTimer().cancel();
                        game[0].getTimer().purge();
                        game[0].getTimer().setRunning(false);
                        gameRepository.saveAndFlush(game[0]);
                        break;
                    }
                }
                game[0].getTimer().cancel();
                game[0].setGameState(getNextState(game[0]));
                game[0].setStartTimeSeconds(TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis()));
                update(game[0],game[0].getGameState(),game[0].getStartTimeSeconds());
            }
        };
        game[0].getTimer().schedule(timerTask,0,1000);
    }
    public boolean getCancel(Game game){
        Optional<Game> updated = gameRepository.findByLobbyId(game.getLobbyId());
        return updated.map(value -> value.getTimer().isCancel()).orElse(false);

    }

    public GameState getNextState(Game game){
        GameState nextGameState;
        GameState currentGameState = game.getGameState();
        switch (currentGameState){
            case PICKWORDSTATE:
                nextGameState = GameState.ENTERCLUESSTATE;
                break;
            case ENTERCLUESSTATE:
                nextGameState = GameState.VOTEONCLUESSTATE;
                break;
            case VOTEONCLUESSTATE:
                nextGameState = GameState.ENTERGUESSSTATE;
                break;
            case ENTERGUESSSTATE:
                nextGameState = GameState.TRANSITIONSTATE;
                break;
            case TRANSITIONSTATE:
                nextGameState = GameState.PICKWORDSTATE;
                break;
            default:
                nextGameState = null;
        }
        return nextGameState;
    }

    public void update(Game game, GameState gameState,long startTime){
        System.out.println("New timer");
        gameRepository.saveAndFlush(game);
        TimerTask timerTask = new TimerTask() {
            @Override
            public void run() {
                game.setTime(TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis()) - startTime);
                if(game.getTime()>= 60 && game.getRoundsPlayed() < 13)  {
                    game.getTimer().cancel();
                    game.getTimer().purge();
                    game.setGameState(getNextState(game));
                    game.setStartTimeSeconds(TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis()));
                    if(game.getGameState().equals(GameState.PICKWORDSTATE)){
                        game.setRoundsPlayed(game.getRoundsPlayed() + 1);
                    }
                    gameRepository.saveAndFlush(game);
                    System.out.println("Rounds played: " + game.getRoundsPlayed());
                    update(game,game.getGameState(),game.getStartTimeSeconds());
                } if(game.getTime() >= 60 && game.getRoundsPlayed() >= 3){
                    game.getTimer().cancel();
                    game.getTimer().purge();
                }
            }
        };
        game.getTimer().cancel();
        game.setTimer(new InternalTimer());
        gameRepository.saveAndFlush(game);
        game.getTimer().schedule(timerTask,1000,1000);
    }

    public void setTimer(Game game) {
        game.setTimer(new InternalTimer());
        game.getTimer().setCancel(false);
    }
}


