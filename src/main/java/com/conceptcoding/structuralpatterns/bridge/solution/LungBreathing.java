package com.conceptcoding.structuralpatterns.bridge.solution;

// Step 2: Concrete Implementor (various breathing processes)
public class LungBreathing implements BreathingProcess {
    @Override
    public void breathe() {
        System.out.println("Breathing through lungs.");
    }
}
