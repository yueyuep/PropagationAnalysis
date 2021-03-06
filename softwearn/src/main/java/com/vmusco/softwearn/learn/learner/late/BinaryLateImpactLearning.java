package com.vmusco.softwearn.learn.learner.late;

import com.vmusco.softminer.graphs.EdgeIdentity;
import com.vmusco.softwearn.learn.LearningKGraph;

/**
 * 
 * @author Vincenzo Musco - http://www.vmusco.com
 */
public class BinaryLateImpactLearning extends LateImpactLearner {

	public BinaryLateImpactLearning(int maxk) {
		super(maxk);
	}
	
	public BinaryLateImpactLearning(int maxk, int kspnr) {
		super(maxk, kspnr);
	}
	
	@Override
	public void updatePath(LearningKGraph g, EdgeIdentity edge, String test, String point) {
		g.setEdgeThreshold(edge.getFrom(), edge.getTo(), 1f);
	}

	@Override
	public float defaultInitWeight() {
		return 0;
	}

	@Override
	public void postDeclareAnImpact(String change, String[] tests) {
	}
	
}
