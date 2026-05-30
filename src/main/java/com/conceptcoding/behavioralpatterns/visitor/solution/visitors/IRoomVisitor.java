package com.conceptcoding.behavioralpatterns.visitor.solution.visitors;

import com.conceptcoding.behavioralpatterns.visitor.solution.elements.DeluxeRoom;
import com.conceptcoding.behavioralpatterns.visitor.solution.elements.StandardRoom;
import com.conceptcoding.behavioralpatterns.visitor.solution.elements.SuiteRoom;

// Visitor interface - defines operations
public interface IRoomVisitor {
    void visitStandardRoom(StandardRoom room);

    void visitDeluxeRoom(DeluxeRoom room);

    void visitSuiteRoom(SuiteRoom room);
}
