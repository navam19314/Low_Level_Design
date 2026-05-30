package com.conceptcoding.behavioralpatterns.visitor.solution.elements;

import com.conceptcoding.behavioralpatterns.visitor.solution.visitors.IRoomVisitor;

// Element interface - represents rooms(elements) that can be visited
public interface IRoom {
    void accept(IRoomVisitor visitor);

}
