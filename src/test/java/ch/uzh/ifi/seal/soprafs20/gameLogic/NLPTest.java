package ch.uzh.ifi.seal.soprafs20.gameLogic;

import ch.uzh.ifi.seal.soprafs20.GameLogic.NLP;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import static org.junit.jupiter.api.Assertions.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class NLPTest {

    private NLP nlp;

    @BeforeAll
    void setUp() {
        nlp = new NLP();
    }

    @Test
    void porterStemmer_test() {
        String word = "laughing";
        String stem = nlp.stemWord(word);
        assertNotEquals(word, stem);
    }

    @Test
    void porterStemmer_fictionalWord_test() {
        String elvishWord = "suilon";
        String stem = nlp.stemWord(elvishWord);
        assertEquals(elvishWord, stem);
    }

    @Test
    void regex_twoWords() {
        String twoWords = "James Bond";
        String word = "agent";

        assertFalse(nlp.checkClue(twoWords, word));
    }

    @Test
    void regex_charsAndDigits() {
        String charsAndDigits = "mus1c";
        String word = "piano";

        assertFalse(nlp.checkClue(charsAndDigits, word));
    }

    @Test
    void regex_charsOnly() {
        String charsOnly = "music";
        String word = "piano";

        assertTrue(nlp.checkClue(charsOnly, word));
    }

    @Test
    public void regex_digitsOnly() {
        String digitsOnly = "007";
        String word = "James Bond";

        assertTrue(nlp.checkClue(digitsOnly, word));
    }

    @Test
    void clue_contains_word() {
        String clue = "rainumbrella";
        String word = "umbrella";

        assertFalse(nlp.checkClue(clue, word));
    }

    @Test
    void word_contains_clue() {
        String clue = "electric";
        String word = "electricity";

        assertFalse(nlp.checkClue(clue, word));
    }

    @Test
     void editDistance_isOne() {
        String clue = "syrup";
        String word = "sirup";

        assertFalse(nlp.checkClue(clue, word));
    }

}
