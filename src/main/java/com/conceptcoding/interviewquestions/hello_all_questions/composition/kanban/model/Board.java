package com.conceptcoding.interviewquestions.hello_all_questions.composition.kanban.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

// One board = one lock. Every change (add / move / remove a card or lane) is synchronized
// on the board, so a card is never in two lanes, or none, even when two people drag at once.
// Different boards never wait for each other.
public class Board {

    private final String id;
    private final String name;
    private final Set<String> memberIds = new LinkedHashSet<>();
    private final List<Lane> lanes = new ArrayList<>();                 // display order, left to right
    private final Map<String, Lane> lanesById = new HashMap<>();
    private final Map<String, Card> cardsById = new HashMap<>();
    private final Map<String, String> laneIdOfCard = new HashMap<>();   // index: cardId → laneId

    public Board(String id, String name, String ownerId) {
        this.id = id;
        this.name = name;
        memberIds.add(ownerId);
    }

    // ---------- members ----------
    public synchronized void addMember(String userId)    { memberIds.add(userId); }
    public synchronized boolean isMember(String userId)  { return memberIds.contains(userId); }

    // ---------- lanes ----------
    public synchronized void addLane(Lane lane) {
        if (lanesById.containsKey(lane.getId())) throw new IllegalArgumentException("Lane exists: " + lane.getId());
        lanes.add(lane);
        lanesById.put(lane.getId(), lane);
    }

    public synchronized void moveLane(String laneId, int position) {
        Lane lane = requireLane(laneId);
        checkPosition(position, lanes.size() - 1);
        lanes.remove(lane);
        lanes.add(position, lane);
    }

    public synchronized void removeLane(String laneId) {
        Lane lane = requireLane(laneId);
        if (lane.size() > 0) throw new IllegalStateException("Move or delete the cards in '" + lane.getName() + "' first");
        lanes.remove(lane);
        lanesById.remove(laneId);
    }

    // ---------- cards ----------
    public synchronized void addCard(String laneId, Card card) {
        Lane lane = requireLane(laneId);
        if (lane.isFull()) throw new IllegalStateException("'" + lane.getName() + "' is at its WIP limit of " + lane.getWipLimit());
        lane.insert(card, lane.size());                       // new cards go to the bottom
        cardsById.put(card.getId(), card);
        laneIdOfCard.put(card.getId(), laneId);
    }

    // Move to another lane OR reorder inside the same lane. position = index in the target lane
    // after the move (0 = top). Everything is validated BEFORE anything changes, so a rejected
    // move leaves the board exactly as it was.
    public synchronized void moveCard(String cardId, String toLaneId, int position) {
        Card card = requireCard(cardId);
        Lane from = lanesById.get(laneIdOfCard.get(cardId));
        Lane to = requireLane(toLaneId);
        boolean sameLane = from == to;

        if (!sameLane && to.isFull()) {
            throw new IllegalStateException("'" + to.getName() + "' is at its WIP limit of " + to.getWipLimit());
        }
        int maxPosition = sameLane ? to.size() - 1 : to.size();   // same lane: the card itself doesn't count twice
        checkPosition(position, maxPosition);

        from.remove(card);
        to.insert(card, position);
        laneIdOfCard.put(cardId, toLaneId);
    }

    public synchronized void removeCard(String cardId) {
        Card card = requireCard(cardId);
        lanesById.get(laneIdOfCard.get(cardId)).remove(card);
        cardsById.remove(cardId);
        laneIdOfCard.remove(cardId);
    }

    public synchronized void updateCard(String cardId, String title, String description) {
        Card card = requireCard(cardId);
        if (title != null) {
            if (title.isBlank()) throw new IllegalArgumentException("Title can't be empty");
            card.setTitle(title);
        }
        if (description != null) card.setDescription(description);
    }

    public synchronized void assignCard(String cardId, String userId) {
        if (userId != null && !memberIds.contains(userId)) throw new IllegalArgumentException(userId + " is not on this board");
        requireCard(cardId).setAssigneeId(userId);
    }

    // ---------- reads ----------
    public synchronized String getLaneIdOfCard(String cardId) {
        requireCard(cardId);
        return laneIdOfCard.get(cardId);
    }

    public synchronized List<Lane> getLanes() { return new ArrayList<>(lanes); }

    // consistent snapshot for the UI: taken under the lock, so no half-finished move is visible
    public synchronized String render() {
        StringBuilder sb = new StringBuilder();
        for (Lane lane : lanes) {
            sb.append(String.format("  %-6s %s%n", lane.getName(), lane.getCards()));
        }
        return sb.toString();
    }

    public synchronized int cardCount() { return cardsById.size(); }

    public String getId()   { return id; }
    public String getName() { return name; }

    // ---------- helpers ----------
    private Lane requireLane(String laneId) {
        Lane lane = lanesById.get(laneId);
        if (lane == null) throw new NoSuchElementException("No lane " + laneId + " on board " + name);
        return lane;
    }

    private Card requireCard(String cardId) {
        Card card = cardsById.get(cardId);
        if (card == null) throw new NoSuchElementException("No card " + cardId + " on board " + name);
        return card;
    }

    private static void checkPosition(int position, int max) {
        if (position < 0 || position > max) {
            throw new IndexOutOfBoundsException("Position " + position + " is outside 0.." + max);
        }
    }
}
