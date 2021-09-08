package it.unive.lisa.cron.dataflow;

import static it.unive.lisa.LiSAFactory.getDefaultFor;

import org.junit.Test;

import it.unive.lisa.AnalysisSetupException;
import it.unive.lisa.AnalysisTestExecutor;
import it.unive.lisa.LiSAConfiguration;
import it.unive.lisa.analysis.AbstractState;
import it.unive.lisa.analysis.dataflow.ReachingDefinitions;
import it.unive.lisa.analysis.heap.HeapDomain;

public class ReachingDefinitionsTest extends AnalysisTestExecutor {

	@Test
	public void testReachingDefinitions() throws AnalysisSetupException {
		LiSAConfiguration conf = new LiSAConfiguration().setDumpAnalysis(true).setAbstractState(
				getDefaultFor(AbstractState.class, getDefaultFor(HeapDomain.class), new ReachingDefinitions()));
		perform("reaching-definitions", "reaching-definitions.imp", conf);
	}
}