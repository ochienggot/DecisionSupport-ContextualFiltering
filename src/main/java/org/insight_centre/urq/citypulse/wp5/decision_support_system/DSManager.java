/*******************************************************************************
 * The MIT License (MIT)
 *
 * Copyright (c) 2014-2015 Stefano Germano - Insight Centre for Data Analytics NUIG
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 *******************************************************************************/
package org.insight_centre.urq.citypulse.wp5.decision_support_system;

import java.util.List;
import java.util.logging.Logger;

import citypulse.commons.reasoning_request.ARType;
import citypulse.commons.reasoning_request.Answer;
import citypulse.commons.reasoning_request.Answers;
import citypulse.commons.reasoning_request.ReasoningRequest;
import citypulse.commons.reasoning_request.User;
import citypulse.commons.reasoning_request.concrete.IntegerFunctionalConstraintValue;
import citypulse.commons.reasoning_request.concrete.StringFunctionalParameterValue;
import citypulse.commons.reasoning_request.functional_requirements.FunctionalConstraint;
import citypulse.commons.reasoning_request.functional_requirements.FunctionalConstraintName;
import citypulse.commons.reasoning_request.functional_requirements.FunctionalConstraintOperator;
import citypulse.commons.reasoning_request.functional_requirements.FunctionalConstraints;
import citypulse.commons.reasoning_request.functional_requirements.FunctionalDetails;
import citypulse.commons.reasoning_request.functional_requirements.FunctionalParameter;
import citypulse.commons.reasoning_request.functional_requirements.FunctionalParameterName;
import citypulse.commons.reasoning_request.functional_requirements.FunctionalParameters;
import citypulse.commons.reasoning_request.functional_requirements.FunctionalPreference;
import citypulse.commons.reasoning_request.functional_requirements.FunctionalPreferenceOperation;
import citypulse.commons.reasoning_request.functional_requirements.FunctionalPreferences;

/**
 * The main class of the Decision Support System
 *
 * @author Stefano Germano
 *
 */
public class DSManager {

	/**
	 * Reference to the Logger class
	 */
	public static Logger Log = Logger.getLogger(DSManager.class.getPackage()
			.getName());

	/**
	 * Generates a simple Reasoning Request (for testing)
	 *
	 * @return a Reasoning Request
	 */
	private static ReasoningRequest getTravelPlannerTestReasoningRequest() {
		final FunctionalParameters functionalParameters = new FunctionalParameters();
		// functionalParameters.addFunctionalParameter(new FunctionalParameter(
		// FunctionalParameterName.STARTING_POINT,
		// new CoordinateFunctionalParameterValue(new Coordinate(
		// 10.116919f, 56.226144f))));
		// functionalParameters.addFunctionalParameter(new FunctionalParameter(
		// FunctionalParameterName.ENDING_POINT,
		// new CoordinateFunctionalParameterValue(new Coordinate(
		// 10.1591864f, 56.1481156f))));
		functionalParameters.addFunctionalParameter(new FunctionalParameter(
				FunctionalParameterName.STARTING_POINT,
				new StringFunctionalParameterValue("10.116919 56.226144")));
		functionalParameters.addFunctionalParameter(new FunctionalParameter(
				FunctionalParameterName.ENDING_POINT,
				new StringFunctionalParameterValue("10.1591864 56.1481156")));

		final FunctionalConstraints functionalConstraints = new FunctionalConstraints();
		functionalConstraints.addFunctionalConstraint(new FunctionalConstraint(
				FunctionalConstraintName.POLLUTION,
				FunctionalConstraintOperator.LESS_THAN,
				new IntegerFunctionalConstraintValue(135)));

		final FunctionalPreferences functionalPreferences = new FunctionalPreferences();
		functionalPreferences.addFunctionalPreference(new FunctionalPreference(
				1, FunctionalPreferenceOperation.MINIMIZE,
				FunctionalConstraintName.TIME));
		functionalPreferences.addFunctionalPreference(new FunctionalPreference(
				2, FunctionalPreferenceOperation.MINIMIZE,
				FunctionalConstraintName.DISTANCE));

		final ReasoningRequest reasoningRequest = new ReasoningRequest(
				new User(), ARType.TRAVEL_PLANNER, new FunctionalDetails(
						functionalParameters, functionalConstraints,
						functionalPreferences));
		return reasoningRequest;
	}

	/**
	 * The starting function of the Decision Support System component
	 *
	 * @param args - not used
	 */
	public static void main(final String[] args) {

		new DSManager();
		DSManager.startDSS(DSManager.getTravelPlannerTestReasoningRequest());

	}

	/**
	 * Starts the Decision Support System
	 *
	 * @param reasoningRequest a Reasoning Request
	 * @return the Answers to the Reasoning Request
	 */
	public static Answers startDSS(final ReasoningRequest reasoningRequest) {

		long starting_time = System.currentTimeMillis();
		final List<String> rules = RequestRewriter.getInstance().getRules(
				reasoningRequest);
		final long ending_time = System.currentTimeMillis();
		DSManager.Log.info("DS_REWRITER_TIME(milisecond): "
				+ (ending_time - starting_time));

		starting_time = ending_time;

		final DummyCoreEngine dummyCoreEngine = new DummyCoreEngine();



		final Answers answers = dummyCoreEngine.performReasoning(
				reasoningRequest, rules);



		for (final Answer answer : answers.getAnswers()) {
			System.out.println(answer);
		}
		DSManager.Log.info("DS_DummyCoreEngine_TIME(milisecond): "
				+ (System.currentTimeMillis() - starting_time));
		return answers;

	}

}
