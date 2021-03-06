package ch.uzh.ifi.seal.soprafs20.controller;

import ch.uzh.ifi.seal.soprafs20.GameLogic.GameState;
import ch.uzh.ifi.seal.soprafs20.entity.*;
import ch.uzh.ifi.seal.soprafs20.exceptions.BadRequestException;
import ch.uzh.ifi.seal.soprafs20.exceptions.UnauthorizedException;
import ch.uzh.ifi.seal.soprafs20.rest.dto.CluePutDTO;
import ch.uzh.ifi.seal.soprafs20.rest.dto.MessagePutDTO;
import ch.uzh.ifi.seal.soprafs20.rest.dto.VotePutDTO;
import ch.uzh.ifi.seal.soprafs20.service.GameService;
import ch.uzh.ifi.seal.soprafs20.service.LobbyService;
import ch.uzh.ifi.seal.soprafs20.service.PlayerService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.Matchers.*;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(GameController.class)
class GameControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @MockBean
    private PlayerService playerService;
    @MockBean
    private GameService gameService;
    @MockBean
    private LobbyService lobbyService;


    @Test
    void givenGame_whenGetGame_returnJson() throws Exception {
        Player player1 = new Player();
        player1.setId(1L);
        player1.setToken("token1");

        Player player2 = new Player();
        player2.setId(2L);
        player2.setToken("token2");

        Clue invalidClue = new Clue();
        invalidClue.setPlayerId(1L);
        invalidClue.setActualClue("anyClue");

        Lobby lobby = new Lobby();
        lobby.setCurrentNumBots(0);

        Game game = new Game();
        game.setLobbyId(1L);
        game.setRoundsPlayed(1);
        game.addPlayer(player1);
        game.addPlayer(player2);
        game.setCurrentGuesser(player1);
        game.setCurrentWord("Erdbeermarmeladebrot");
        game.setCurrentGuess("Bananenbrot");
        game.setGameState(GameState.ENTER_GUESS_STATE);
        game.addInvalidClue(invalidClue);
        game.addClue(invalidClue);

        given(gameService.getGame(Mockito.anyLong())).willReturn(game);
        given(lobbyService.getLobby(Mockito.anyLong())).willReturn(lobby);

        MockHttpServletRequestBuilder getRequest = get("/lobbies/{lobbyId}/game", game.getLobbyId())
                .contentType(MediaType.APPLICATION_JSON)
                .param("token", player2.getToken());

        mockMvc.perform(getRequest)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lobbyId", is(game.getLobbyId().intValue())))
                .andExpect(jsonPath("$.roundsPlayed", is(game.getRoundsPlayed())))
                .andExpect(jsonPath("$.players", hasSize(2)))
                .andExpect(jsonPath("$.currentWord", is(game.getCurrentWord())))
                .andExpect(jsonPath("$.currentGuess", is(game.getCurrentGuess())))
                .andExpect(jsonPath("$.enteredClues", hasSize(1)))
                .andExpect(jsonPath("$.invalidClues", hasSize(1)));
    }

    @Test
    void givenGameWithBots_whenGetGame_returnBotsInJson() throws Exception {
        Player player1 = new Player();
        player1.setId(1L);
        player1.setUsername("username1");
        player1.setToken("token1");

        Player player2 = new Player();
        player2.setId(2L);
        player2.setUsername("username2");
        player2.setToken("token2");

        Clue invalidClue = new Clue();
        invalidClue.setPlayerId(1L);
        invalidClue.setActualClue("anyClue");

        Lobby lobby = new Lobby();
        lobby.setCurrentNumBots(2);

        Game game = new Game();
        game.setLobbyId(1L);
        game.setRoundsPlayed(1);
        game.addPlayer(player1);
        game.addPlayer(player2);
        game.setCurrentGuesser(player1);
        game.setCurrentWord("Erdbeermarmeladebrot");
        game.setCurrentGuess("Bananenbrot");
        game.setGameState(GameState.ENTER_GUESS_STATE);
        game.addInvalidClue(invalidClue);
        game.addClue(invalidClue);

        given(gameService.getGame(Mockito.anyLong())).willReturn(game);
        given(lobbyService.getLobby(Mockito.anyLong())).willReturn(lobby);

        MockHttpServletRequestBuilder getRequest = get("/lobbies/{lobbyId}/game", game.getLobbyId())
                .contentType(MediaType.APPLICATION_JSON)
                .param("token", player2.getToken());

        mockMvc.perform(getRequest)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lobbyId", is(game.getLobbyId().intValue())))
                .andExpect(jsonPath("$.roundsPlayed", is(game.getRoundsPlayed())))
                .andExpect(jsonPath("$.players", hasSize(4)))
                .andExpect(jsonPath("$.players[3].username", is("bot!")))
                .andExpect(jsonPath("$.currentWord", is(game.getCurrentWord())))
                .andExpect(jsonPath("$.currentGuess", is(game.getCurrentGuess())))
                .andExpect(jsonPath("$.enteredClues", hasSize(1)))
                .andExpect(jsonPath("$.invalidClues", hasSize(1)));
    }

    @Test
    void getGame_guesserToken_returnJson() throws Exception {
        Player player1 = new Player();
        player1.setId(1L);
        player1.setToken("token1");

        Player player2 = new Player();
        player2.setId(2L);
        player2.setToken("token2");

        Lobby lobby = new Lobby();
        lobby.setCurrentNumBots(0);

        Game game = new Game();
        game.setLobbyId(1L);
        game.setRoundsPlayed(1);
        game.addPlayer(player1);
        game.addPlayer(player2);
        game.setCurrentGuesser(player1);
        game.setCurrentWord("Erdbeermarmeladebrot");
        game.setGameState(GameState.PICK_WORD_STATE);

        given(gameService.getGame(Mockito.anyLong())).willReturn(game);
        given(lobbyService.getLobby(Mockito.anyLong())).willReturn(lobby);

        MockHttpServletRequestBuilder getRequest = get("/lobbies/{lobbyId}/game", game.getLobbyId())
                .contentType(MediaType.APPLICATION_JSON)
                .param("token", player1.getToken());

        mockMvc.perform(getRequest)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lobbyId", is(game.getLobbyId().intValue())))
                .andExpect(jsonPath("$.roundsPlayed", is(game.getRoundsPlayed())))
                .andExpect(jsonPath("$.players", hasSize(2)))
                .andExpect(jsonPath("$.currentWord", is(nullValue())));
    }

    @Test
    void getGame_invalidToken_throwsException() throws Exception{
        Player player1 = new Player();
        player1.setId(1L);
        player1.setToken("token1");

        Lobby lobby = new Lobby();
        lobby.setCurrentNumBots(0);

        Game game = new Game();
        game.setLobbyId(1L);
        game.setRoundsPlayed(1);
        game.addPlayer(player1);
        game.setCurrentGuesser(player1);
        game.setCurrentWord("Erdbeermarmeladebrot");

        given(gameService.getGame(Mockito.anyLong())).willReturn(game);
        given(lobbyService.getLobby(Mockito.anyLong())).willReturn(lobby);

        MockHttpServletRequestBuilder getRequest = get("/lobbies/{lobbyId}/game", game.getLobbyId())
                .contentType(MediaType.APPLICATION_JSON)
                .param("token", "wrongToken");

        mockMvc.perform(getRequest)
                .andExpect(status().isUnauthorized());
    }

    @Test
    void sendClue_validInput() throws Exception{
        Player player1 = new Player();
        player1.setId(1L);
        player1.setToken("token1");

        Player player2 = new Player();
        player2.setId(2L);
        player2.setToken("token2");

        InternalTimer timer = new InternalTimer();

        Game game = new Game();
        game.setLobbyId(1L);
        game.setRoundsPlayed(1);
        game.addPlayer(player1);
        game.setCurrentGuesser(player1);
        game.setCurrentWord("Erdbeermarmeladebrot");
        game.setGameState(GameState.ENTER_CLUES_STATE);
        game.setTimer(timer);

        CluePutDTO cluePutDTO = new CluePutDTO();
        cluePutDTO.setMessage("Zopf");
        cluePutDTO.setPlayerId(player2.getId());
        cluePutDTO.setPlayerToken(player2.getToken());

        given(playerService.getPlayer(Mockito.any())).willReturn(player1);
        given(gameService.getGame(Mockito.anyLong())).willReturn(game);
        given(gameService.sendClue(Mockito.any(), Mockito.any(), Mockito.any())).willReturn(true);

        MockHttpServletRequestBuilder putRequest = put("/lobbies/{lobbyId}/game/clue", game.getLobbyId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(cluePutDTO));

        mockMvc.perform(putRequest)
                .andExpect(status().isNoContent());
    }

    @Test
    void sendClue_validInput_wrongState() throws Exception {
        Player player1 = new Player();
        player1.setId(1L);
        player1.setToken("token1");

        Player player2 = new Player();
        player2.setId(2L);
        player2.setToken("token2");

        Game game = new Game();
        game.setLobbyId(1L);
        game.setRoundsPlayed(1);
        game.addPlayer(player1);
        game.setGameState(GameState.PICK_WORD_STATE);

        CluePutDTO cluePutDTO = new CluePutDTO();
        cluePutDTO.setMessage("Zopf");
        cluePutDTO.setPlayerId(player2.getId());
        cluePutDTO.setPlayerToken(player2.getToken());

        //given(gameService.getGame(Mockito.anyLong())).willReturn(game);
        given(playerService.getPlayer(Mockito.any())).willReturn(player1);

        given(gameService.sendClue(Mockito.any(), Mockito.any(), Mockito.any())).willThrow(new UnauthorizedException("ex"));
        given(playerService.getPlayer(Mockito.anyLong())).willReturn(player1);

        MockHttpServletRequestBuilder putRequest = put("/lobbies/{lobbyId}/game/clue", game.getLobbyId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(cluePutDTO));

        mockMvc.perform(putRequest)
                .andExpect(status().isUnauthorized());
    }

    @Test
    void pickWord_validInput_success() throws Exception {
        Player player1 = new Player();
        player1.setId(1L);
        player1.setToken("token1");

        Player player2 = new Player();
        player2.setId(2L);
        player2.setToken("token2");

        InternalTimer timer = new InternalTimer();

        Game game = new Game();
        game.setLobbyId(1L);
        game.setRoundsPlayed(1);
        game.addPlayer(player1);
        game.setCurrentGuesser(player1);
        game.setGameState(GameState.PICK_WORD_STATE);
        game.setTimer(timer);
        game.setStartTimeSeconds(TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis()));

        given(gameService.getGame(Mockito.anyLong())).willReturn(game);
        given(gameService.pickWord(Mockito.any(), Mockito.any())).willReturn(true);

        MockHttpServletRequestBuilder getRequest = get("/lobbies/{lobbyId}/game/word", game.getLobbyId())
                .contentType(MediaType.APPLICATION_JSON)
                .param("token", player1.getToken());

        mockMvc.perform(getRequest)
                .andExpect(status().isOk());
    }

    @Test
    void pickWord_validInput_wrongState() throws Exception {
        Player player1 = new Player();
        player1.setId(1L);
        player1.setToken("token1");

        Player player2 = new Player();
        player2.setId(2L);
        player2.setToken("token2");

        Game game = new Game();
        game.setLobbyId(1L);
        game.setRoundsPlayed(1);
        game.addPlayer(player1);
        game.setCurrentGuesser(player1);
        game.setGameState(GameState.TRANSITION_STATE);
        game.setStartTimeSeconds(TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis()));

        given(gameService.getGame(Mockito.anyLong())).willReturn(game);

        MockHttpServletRequestBuilder getRequest = get("/lobbies/{lobbyId}/game/word", game.getLobbyId())
                .contentType(MediaType.APPLICATION_JSON)
                .param("token", player1.getToken());

        mockMvc.perform(getRequest).andExpect(status().is4xxClientError());
    }
    @Test
    void sendGuess_validInput_success() throws Exception {
        Player player1 = new Player();
        player1.setId(1L);
        player1.setToken("token1");

        InternalTimer timer = new InternalTimer();

        Game game = new Game();
        game.setLobbyId(1L);
        game.setRoundsPlayed(1);
        game.addPlayer(player1);
        game.setCurrentGuesser(player1);
        game.setGameState(GameState.ENTER_GUESS_STATE);
        game.setTimer(timer);
        game.setStartTimeSeconds(TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis()));

        MessagePutDTO messagePutDTO = new MessagePutDTO();
        messagePutDTO.setPlayerToken(player1.getToken());
        messagePutDTO.setPlayerId(player1.getId());
        messagePutDTO.setMessage("anyClue");

        given(gameService.getGame(Mockito.anyLong())).willReturn(game);

        MockHttpServletRequestBuilder putRequest = put("/lobbies/{lobbyId}/game/guess", game.getLobbyId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(messagePutDTO));

        mockMvc.perform(putRequest).andExpect(status().isNoContent());
    }

    @Test
    void vote_validInput_success() throws Exception {
        Player player1 = new Player();
        player1.setId(1L);
        player1.setToken("token1");

        Player player2 = new Player();

        InternalTimer timer = new InternalTimer();

        Game game = new Game();
        game.setLobbyId(1L);
        game.setRoundsPlayed(1);
        game.addPlayer(player1);
        game.setCurrentGuesser(player2);
        game.setGameState(GameState.VOTE_ON_CLUES_STATE);
        game.setTimer(timer);
        game.setStartTimeSeconds(TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis()));

        ArrayList<String> invalidClues = new ArrayList<>();
        invalidClues.add("someClue");
        VotePutDTO votePutDTO = new VotePutDTO();
        votePutDTO.setPlayerId(player1.getId());
        votePutDTO.setPlayerToken(player1.getToken());
        votePutDTO.setInvalidClues(invalidClues);

        given(gameService.getGame(Mockito.anyLong())).willReturn(game);
        given(playerService.getPlayerByToken(Mockito.any())).willReturn(player1);
        given(gameService.vote(Mockito.any(), Mockito.any(), Mockito.any())).willReturn(true);

        MockHttpServletRequestBuilder putRequest = put("/lobbies/{lobbyId}/game/vote", game.getLobbyId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(votePutDTO));

        mockMvc.perform(putRequest).andExpect(status().isNoContent());
    }

    @Test
    void vote_validInput_wrongState() throws Exception {
        Player player1 = new Player();
        player1.setId(1L);
        player1.setToken("token1");

        Player player2 = new Player();

        InternalTimer timer = new InternalTimer();

        Game game = new Game();
        game.setLobbyId(1L);
        game.setRoundsPlayed(1);
        game.addPlayer(player1);
        game.setCurrentGuesser(player2);
        game.setGameState(GameState.ENTER_GUESS_STATE);
        game.setTimer(timer);
        game.setStartTimeSeconds(TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis()));

        ArrayList<String> invalidClues = new ArrayList<>();
        invalidClues.add("someClue");
        VotePutDTO votePutDTO = new VotePutDTO();
        votePutDTO.setPlayerId(player1.getId());
        votePutDTO.setPlayerToken(player1.getToken());
        votePutDTO.setInvalidClues(invalidClues);

        given(gameService.getGame(Mockito.anyLong())).willReturn(game);
        given(playerService.getPlayerByToken(Mockito.any())).willReturn(player1);

        MockHttpServletRequestBuilder putRequest = put("/lobbies/{lobbyId}/game/vote", game.getLobbyId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(votePutDTO));

        mockMvc.perform(putRequest).andExpect(status().isUnauthorized());
    }

    @Test
    public void vote_unauthorizedUser() throws Exception {
        Player player1 = new Player();
        player1.setId(1L);
        player1.setToken("token1");

        InternalTimer timer = new InternalTimer();

        Game game = new Game();
        game.setLobbyId(1L);
        game.setRoundsPlayed(1);
        game.addPlayer(player1);
        game.setCurrentGuesser(player1);
        game.setGameState(GameState.VOTE_ON_CLUES_STATE);
        game.setTimer(timer);
        game.setStartTimeSeconds(TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis()));

        ArrayList<String> invalidClues = new ArrayList<>();
        invalidClues.add("someClue");
        VotePutDTO votePutDTO = new VotePutDTO();
        votePutDTO.setPlayerId(player1.getId());
        votePutDTO.setPlayerToken(player1.getToken());
        votePutDTO.setInvalidClues(invalidClues);

        given(gameService.getGame(Mockito.anyLong())).willReturn(game);
        given(playerService.getPlayerByToken(Mockito.any())).willReturn(player1);

        MockHttpServletRequestBuilder putRequest = put("/lobbies/{lobbyId}/game/vote", game.getLobbyId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(votePutDTO));

        mockMvc.perform(putRequest).andExpect(status().isUnauthorized());
    }

    @Test
    void getTime_Authorized() throws Exception {
        Player player1 = new Player();
        player1.setId(1L);
        player1.setToken("token1");

        InternalTimer timer = new InternalTimer();

        Game game = new Game();
        game.setLobbyId(1L);
        game.setRoundsPlayed(1);
        game.addPlayer(player1);
        game.setCurrentGuesser(player1);
        game.setGameState(GameState.VOTE_ON_CLUES_STATE);
        game.setTimer(timer);
        game.setStartTimeSeconds(TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis()));
        given(gameService.getGame(Mockito.anyLong())).willReturn(game);


        MockHttpServletRequestBuilder getRequest = get("/lobbies/{lobbyId}/game/timer?token=token1", game.getLobbyId())
                .contentType(MediaType.APPLICATION_JSON);

        mockMvc.perform(getRequest).andExpect(status().isOk());

    }

    @Test
    void getTime_Unauthorized() throws Exception {
        Player player1 = new Player();
        player1.setId(1L);
        player1.setToken("token1");

        InternalTimer timer = new InternalTimer();

        Game game = new Game();
        game.setLobbyId(1L);
        game.setRoundsPlayed(1);
        game.addPlayer(player1);
        game.setCurrentGuesser(player1);
        game.setGameState(GameState.VOTE_ON_CLUES_STATE);
        game.setTimer(timer);
        game.setStartTimeSeconds(TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis()));
        given(gameService.getGame(Mockito.anyLong())).willReturn(game);


        MockHttpServletRequestBuilder getRequest = get("/lobbies/{lobbyId}/game/timer?token=aaaa", game.getLobbyId())
                .contentType(MediaType.APPLICATION_JSON);

        mockMvc.perform(getRequest).andExpect(status().isUnauthorized())
        .andExpect(content().string("Not allowed to retrieve timer for this game!"));

    }

    @Test
    public void getTime_EndgameState() throws Exception {
        Player player1 = new Player();
        player1.setId(1L);
        player1.setToken("token1");

        InternalTimer timer = new InternalTimer();

        Game game = new Game();
        game.setLobbyId(1L);
        game.setRoundsPlayed(1);
        game.addPlayer(player1);
        game.setCurrentGuesser(player1);
        game.setGameState(GameState.END_GAME_STATE);
        game.setTimer(timer);
        game.setStartTimeSeconds(TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis()));
        given(gameService.getGame(Mockito.anyLong())).willReturn(game);


        MockHttpServletRequestBuilder getRequest = get("/lobbies/{lobbyId}/game/timer?token=token1", game.getLobbyId())
                .contentType(MediaType.APPLICATION_JSON);

        mockMvc.perform(getRequest).andExpect(status().isOk())
                .andExpect(content().string("0"));
    }

    @Test
    void getTime_actualTime() throws Exception {
        Player player1 = new Player();
        player1.setId(1L);
        player1.setToken("token1");

        InternalTimer timer = new InternalTimer();

        Game game = new Game();
        game.setLobbyId(1L);
        game.setRoundsPlayed(1);
        game.addPlayer(player1);
        game.setCurrentGuesser(player1);
        game.setGameState(GameState.PICK_WORD_STATE);
        game.setTimer(timer);
        game.setStartTimeSeconds(TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis()));
        given(gameService.getGame(Mockito.anyLong())).willReturn(game);
        given(gameService.getMaxTime(game)).willReturn(10);
        Thread.sleep(3000);

        MockHttpServletRequestBuilder getRequest = get("/lobbies/{lobbyId}/game/timer?token=token1", game.getLobbyId())
                .contentType(MediaType.APPLICATION_JSON);

        mockMvc.perform(getRequest).andExpect(status().isOk())
                .andExpect(content().string("7"));
    }

    private String asJsonString(final Object object) {
        try {
            return new ObjectMapper().writeValueAsString(object);
        }
        catch (JsonProcessingException e) {
            throw new BadRequestException(String.format("The request body could not be created.%s", e.toString()));
        }
    }
}
