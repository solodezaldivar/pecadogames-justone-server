package ch.uzh.ifi.seal.soprafs20.repository;


import ch.uzh.ifi.seal.soprafs20.entity.LobbyScore;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Date;
import java.util.List;

@Repository("lobbyScoreRepository")

public interface LobbyScoreRepository extends JpaRepository<LobbyScore, Long> {
    public List<LobbyScore> findAllByOrderByScore();
    public List<LobbyScore> findAllByOrderByDate();

}
