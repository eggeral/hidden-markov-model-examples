package egger.software.hmm.algorithm

import egger.software.hmm.*


// Baum-Welch algorithm as described in
// https://www.ece.ucsb.edu/Faculty/Rabiner/ece259/Reprints/tutorial%20on%20hmm%20and%20applications.pdf
// and
// https://people.eecs.berkeley.edu/~stephentu/writeups/hmm-baum-welch-derivation.pdf
fun <TState, TObservation> HiddenMarkovModel<TState, TObservation>.trainOneStepUsingRabinerBaumWelch(observationsList: List<List<TObservation>>): HiddenMarkovModel<TState, TObservation> {

    val newTransitionProbabilities = stateTransitionTable<TState, TState> {}
    val newEmissionProbabilities = stateTransitionTable<TState, TObservation> {}
    val newInitialProbabilities = mutableListOf<StateWithProbability<TState>>()

    val expectedNumberOfTimesInStateAtTheBeginning = mutableMapOf<TState, Double>().initUsing(states, 0.0)
    val expectedNumberOfTransitionsFromStateToState = mutableMapOf<TState, MutableMap<TState, Double>>()
            .apply { states.forEach { state -> set(state, mutableMapOf<TState, Double>().initUsing(states, 0.0)) } }
    val expectedTotalNumberOfTransitionsAwayFromState = mutableMapOf<TState, Double>().initUsing(states, 0.0)
    val expectedNumberOfTimesInState = mutableMapOf<TState, Double>().initUsing(states, 0.0)
    val expectedNumberOfTimesInStateAndObserving = mutableMapOf<TState, MutableMap<TObservation, Double>>()
            .apply { states.forEach { state -> set(state, mutableMapOf<TObservation, Double>().initUsing(observations, 0.0)) } }

    for (observation in observationsList) {

        val forwardBackwardCalculationResult = this.observing(observation).calculateForwardBackward()

        val probabilityOfBeingInStateAtTimeOne = this.observing(observation).probabilityOfBeingInState(1)
        for (state in this.states) {
            expectedNumberOfTimesInStateAtTheBeginning[state] = expectedNumberOfTimesInStateAtTheBeginning[state]!! + probabilityOfBeingInStateAtTimeOne[state]!!
        }

        for (time in 1 until observation.size) { // note that we go only to time - 1 as the algorithm demands

            val probabilityOfBeingInStateAndTheNextStateIs = this.observing(observation).probabilityOfBeingInStateAndTheNextStateIs(forwardBackwardCalculationResult, time)

            for (sourceState in this.states) {
                for (targetState in this.states) {
                    expectedNumberOfTransitionsFromStateToState[sourceState]!![targetState] = expectedNumberOfTransitionsFromStateToState[sourceState]!![targetState]!! +
                            probabilityOfBeingInStateAndTheNextStateIs.given(sourceState).probabilityOf(targetState)
                }
            }

            val probabilityOfBeingInState = this.observing(observation).probabilityOfBeingInState(time)

            for (sourceState in this.states) {
                expectedTotalNumberOfTransitionsAwayFromState[sourceState] = expectedTotalNumberOfTransitionsAwayFromState[sourceState]!! +
                        probabilityOfBeingInState[sourceState]!!
            }

        }

        for (time in 1..observation.size) {
            for (state in this.states) {
                expectedNumberOfTimesInState[state] = expectedNumberOfTimesInState[state]!! + this.observing(observation).probabilityOfBeingInState(time)[state]!!
            }
        }

        for (time in 1..observation.size) {
            for (sourceState in this.states) {
                for (targetObservation in this.observations) {
                    if (targetObservation == observation[time - 1]) {
                        expectedNumberOfTimesInStateAndObserving[sourceState]!![targetObservation] = expectedNumberOfTimesInStateAndObserving[sourceState]!![targetObservation]!! +
                                this.observing(observation).probabilityOfBeingInState(time)[sourceState]!!
                    }
                }
            }
        }

    }

    for (state in states) {
        newInitialProbabilities.add(state withProbabilityOf (expectedNumberOfTimesInStateAtTheBeginning[state]!! / observationsList.size))
    }

    for (sourceState in this.states) {
        for (targetState in this.states) {
            newTransitionProbabilities.addTransition(sourceState, targetState withProbabilityOf (
                    expectedNumberOfTransitionsFromStateToState[sourceState]!![targetState]!! /
                            expectedTotalNumberOfTransitionsAwayFromState[sourceState]!!))
        }
    }

    for (sourceState in this.states) {
        for (targetObservation in this.observations) {
            newEmissionProbabilities.addTransition(sourceState, targetObservation withProbabilityOf (
                    expectedNumberOfTimesInStateAndObserving[sourceState]!![targetObservation]!! /
                            expectedNumberOfTimesInState[sourceState]!!))
        }
    }


    return HiddenMarkovModel(newInitialProbabilities, newTransitionProbabilities, newEmissionProbabilities)
}

fun <TState, TObservation> HiddenMarkovModelWithObservations<TState, TObservation>.probabilityOfBeingInState(time: Int): Map<TState, Double> {

    val result = mutableMapOf<TState, Double>()
    var totalSum = 0.0
    val alpha = this.probabilityOfObservedSequenceForEachHiddenState(time)
    val beta = this.beta(time)

    for (sourceState in this.hiddenMarkovModel.states) {
        val value = alpha[sourceState]!! * beta[sourceState]!!
        result[sourceState] = value
        totalSum += value
    }

    for (sourceState in this.hiddenMarkovModel.states) {
        result[sourceState] = result[sourceState]!! / totalSum
    }

    return result

}

fun <TState, TObservation> HiddenMarkovModelWithObservations<TState, TObservation>.probabilityOfBeingInStateAndTheNextStateIs(forwardBackwardCalculationResult: ForwardBackwardCalculationResult<TState>, time: Int): StateTransitionTable<TState, TState> {

    val tmp = StateTransitionTable<TState, TState>()
    var totalSum = 0.0
    val alpha = forwardBackwardCalculationResult.forward[time]
    val beta = forwardBackwardCalculationResult.backward[time + 1]

    for (sourceState in this.hiddenMarkovModel.states) {
        for (targetState in this.hiddenMarkovModel.states) {


            val value = alpha[sourceState]!! * beta[targetState]!! *
                    this.hiddenMarkovModel.stateTransitions.given(sourceState).probabilityOf(targetState) *
                    this.hiddenMarkovModel.observationProbabilities.given(targetState).probabilityOf(observations[time])
            tmp.addTransition(sourceState, targetState withProbabilityOf (value))
            totalSum += value

        }
    }

    val result = StateTransitionTable<TState, TState>()

    for (sourceState in this.hiddenMarkovModel.states) {
        for (targetState in this.hiddenMarkovModel.states) {

            val tmpValue = tmp.given(sourceState).probabilityOf(targetState)
            result.addTransition(sourceState, targetState.withProbabilityOf(tmpValue / totalSum))

        }
    }

    return result

}

fun <TState, TObservation> HiddenMarkovModelWithObservations<TState, TObservation>.probabilityOfObservedSequenceForEachHiddenState(time: Int): Map<TState, Double> {

    // "forward" algorithm
    // alternative solution based on the index in the observed sequence
    var alpha = mutableMapOf<TState, Double>()

    // Initialization
    for (state in hiddenMarkovModel.states) {
        val pi = hiddenMarkovModel.startingProbabilityOf(state)
        val b = hiddenMarkovModel.observationProbabilities.given(state) probabilityOf observations[0]
        alpha[state] = pi * b
    }


    for (observationIdx in 1 until time) {
        val previousAlpha = alpha
        alpha = mutableMapOf()

        for (state in hiddenMarkovModel.states) {
            val b = hiddenMarkovModel.observationProbabilities.given(state) probabilityOf observations[observationIdx]

            var incomingSum = 0.0
            for (incomingState in hiddenMarkovModel.states) {
                incomingSum += (hiddenMarkovModel.stateTransitions.given(incomingState) probabilityOf state) * previousAlpha[incomingState]!!
            }
            alpha[state] = incomingSum * b
        }

    }
    return alpha

}

fun <TState, TObservation> HiddenMarkovModelWithObservations<TState, TObservation>.beta(time: Int): Map<TState, Double> {
    // "backward" algorithm
    // alternative solution based on the index in the observed sequence

    var beta = mutableMapOf<TState, Double>()

    // Initialization
    for (state in hiddenMarkovModel.states) {
        beta[state] = 1.0
    }

    for (observation in observations.drop(time - 1).reversed().drop(1)) {
        val previousBeta = beta
        beta = mutableMapOf()

        for (state in hiddenMarkovModel.states) {
            var targetSum = 0.0
            for (targetState in hiddenMarkovModel.states) {
                targetSum += (hiddenMarkovModel.stateTransitions.given(state) probabilityOf targetState) *
                        (hiddenMarkovModel.observationProbabilities.given(targetState) probabilityOf observation) *
                        previousBeta[targetState]!!
            }
            beta[state] = targetSum
        }

    }
    return beta

}

