import ai.koog.agents.core.dsl.builder.strategy
import com.aivashin.model.graph.OptimizerState
import com.aivashin.model.graph.contextAggregatorNode
import com.aivashin.model.graph.finishNode
import com.aivashin.model.graph.queryAnalyzerNode
import com.aivashin.model.graph.rejectNode
import com.aivashin.model.graph.securityGuardNode
import com.aivashin.model.graph.selfReflectionNode
import com.aivashin.model.graph.solutionArchitectNode

val optimizerStrategy = strategy<OptimizerState, String>("db-optimization-pipeline") {

    // --- Topology and Routing Transitions ---
    edge(nodeStart forwardTo securityGuardNode)

    edge(securityGuardNode forwardTo queryAnalyzerNode onCondition { it.isSafe })
    edge(securityGuardNode forwardTo rejectNode onCondition { it.isSafe.not() })

    edge(queryAnalyzerNode forwardTo contextAggregatorNode)
    edge(contextAggregatorNode forwardTo solutionArchitectNode)
    edge(solutionArchitectNode forwardTo selfReflectionNode)

    // The Self-Correction Loop edge
    edge(selfReflectionNode forwardTo solutionArchitectNode onCondition {
        it.validationErrors.isNotEmpty() && it.iterationCount < 3
    })

    // Safe exit edge
    edge(selfReflectionNode forwardTo finishNode onCondition {
        it.validationErrors.isEmpty() || it.iterationCount >= 3
    })

    edge(rejectNode forwardTo finishNode)
    edge(finishNode forwardTo nodeFinish)
}