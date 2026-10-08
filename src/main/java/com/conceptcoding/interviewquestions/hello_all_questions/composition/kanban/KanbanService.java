package com.conceptcoding.interviewquestions.hello_all_questions.composition.kanban;

import com.conceptcoding.interviewquestions.hello_all_questions.composition.kanban.model.Board;
import com.conceptcoding.interviewquestions.hello_all_questions.composition.kanban.model.Card;
import com.conceptcoding.interviewquestions.hello_all_questions.composition.kanban.model.Lane;

import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

// The service the app calls. It checks WHO may act (board members only);
// the Board checks WHAT is valid (lanes exist, WIP limits, positions) under its own lock.
public class KanbanService {

    private final Map<String, Board> boards = new ConcurrentHashMap<>();
    private final AtomicLong seq = new AtomicLong();

    public Board createBoard(String name, String ownerId) {
        Board board = new Board("B-" + seq.incrementAndGet(), name, ownerId);
        boards.put(board.getId(), board);
        return board;
    }

    public void addMember(String boardId, String actorId, String newMemberId) {
        member(boardId, actorId).addMember(newMemberId);
    }

    public Lane addLane(String boardId, String actorId, String name, int wipLimit) {
        Lane lane = new Lane("L-" + seq.incrementAndGet(), name, wipLimit);
        member(boardId, actorId).addLane(lane);
        return lane;
    }

    public Card createCard(String boardId, String actorId, String laneId, String title, String description) {
        if (title == null || title.isBlank()) throw new IllegalArgumentException("Title can't be empty");
        Card card = new Card("C-" + seq.incrementAndGet(), title, description, actorId);
        member(boardId, actorId).addCard(laneId, card);
        return card;
    }

    public void moveCard(String boardId, String actorId, String cardId, String toLaneId, int position) {
        member(boardId, actorId).moveCard(cardId, toLaneId, position);
    }

    public void assignCard(String boardId, String actorId, String cardId, String assigneeId) {
        member(boardId, actorId).assignCard(cardId, assigneeId);
    }

    public void updateCard(String boardId, String actorId, String cardId, String title, String description) {
        member(boardId, actorId).updateCard(cardId, title, description);
    }

    public void deleteCard(String boardId, String actorId, String cardId) {
        member(boardId, actorId).removeCard(cardId);
    }

    public Board getBoard(String boardId, String actorId) {
        return member(boardId, actorId);
    }

    private Board member(String boardId, String actorId) {
        Board board = boards.get(boardId);
        if (board == null) throw new NoSuchElementException("Board not found: " + boardId);
        if (!board.isMember(actorId)) throw new SecurityException(actorId + " is not a member of " + board.getName());
        return board;
    }
}
