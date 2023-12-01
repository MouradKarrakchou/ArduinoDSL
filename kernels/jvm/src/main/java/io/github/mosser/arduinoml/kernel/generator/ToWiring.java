package io.github.mosser.arduinoml.kernel.generator;

import io.github.mosser.arduinoml.kernel.App;
import io.github.mosser.arduinoml.kernel.behavioral.*;
import io.github.mosser.arduinoml.kernel.structural.*;

/**
 * Quick and dirty visitor to support the generation of Wiring code
 */
public class ToWiring extends Visitor<StringBuffer> {
	enum PASS {ONE, TWO}
	enum COND_PASS {ONE, TWO, THREE, FOUR}


	public ToWiring() {
		this.result = new StringBuffer();
	}

	private void w(String s) {
		result.append(String.format("%s",s));
	}

	@Override
	public void visit(App app) {
		//first pass, create global vars
		context.put("pass", PASS.ONE);
		w("// Wiring code generated from an ArduinoML model\n");
		w(String.format("// Application name: %s\n", app.getName())+"\n");

		w("long debounce = 200;\n");
		w("\nenum STATE {");
		String sep ="";
		for(State state: app.getStates()){
			w(sep);
			state.accept(this);
			sep=", ";
		}
		w("};\n");
		if (app.getInitial() != null) {
			w("STATE currentState = " + app.getInitial().getName()+";\n");
		}

		for(Brick brick: app.getBricks()){
			brick.accept(this);
		}

		//second pass, setup and loop
		context.put("pass",PASS.TWO);
		w("\nvoid setup(){\n");
		for(Brick brick: app.getBricks()){
			brick.accept(this);
		}
		w("}\n");

		w("\nvoid loop() {\n" +
			"\tswitch(currentState){\n");
		for(State state: app.getStates()){
			state.accept(this);
		}
		w("\t}\n" +
			"}");
	}

	@Override
	public void visit(Actuator actuator) {
		if(context.get("pass") == PASS.ONE) {
			return;
		}
		if(context.get("pass") == PASS.TWO) {
			w(String.format("  pinMode(%d, OUTPUT); // %s [Actuator]\n", actuator.getPin(), actuator.getName()));
		}
	}


	@Override
	public void visit(Sensor sensor) {
		if(context.get("pass") == PASS.ONE) {
			w(String.format("\nboolean %sBounceGuard = false;\n", sensor.getName()));
			w(String.format("long %sLastDebounceTime = 0;\n", sensor.getName()));
			return;
		}
		if(context.get("pass") == PASS.TWO) {
			w(String.format("  pinMode(%d, INPUT);  // %s [Sensor]\n", sensor.getPin(), sensor.getName()));
		}
	}

	@Override
	public void visit(State state) {
		if(context.get("pass") == PASS.ONE){
			w(state.getName());
			return;
		}
		if(context.get("pass") == PASS.TWO) {
			w("\t\tcase " + state.getName() + ":\n");
			for (Action action : state.getActions()) {
				action.accept(this);
			}

			if (state.getTransitions() != null) {
				for(Transition transition : state.getTransitions()) {
					transition.accept(this);
				}
				w("\t\tbreak;\n");
			}
		}

	}

	@Override
	public void visit(Transition transition) {
		if(context.get("pass") == PASS.ONE) {
			return;
		}
		if(context.get("pass") == PASS.TWO) {
			context.put("cond_pass", COND_PASS.ONE);
			for (Condition condition: transition.getConditions()) {
				condition.accept(this);
			}
			w(String.format("\t\t\tif ("));
			context.put("cond_pass", COND_PASS.TWO);
			for (Condition condition: transition.getConditions()) {
				condition.accept(this);
			}
			context.put("cond_pass", COND_PASS.THREE);
			for (Condition condition: transition.getConditions()) {
				condition.accept(this);
			}
			w(String.format(") {\n"));
			context.put("cond_pass", COND_PASS.FOUR);
			for (Condition condition: transition.getConditions()) {
				condition.accept(this);
			}
			w("\t\t\t\tcurrentState = " + transition.getNext().getName() + ";\n");
			w("\t\t\t}\n");
        }
	}

	@Override
	public void visit(Condition condition) {
		if(context.get("cond_pass") == COND_PASS.ONE) {
			String sensorName = condition.getSensor().getName();
			w(String.format("\t\t\t%sBounceGuard = millis() - %sLastDebounceTime > debounce;\n",
					sensorName, sensorName));
		}
		if(context.get("cond_pass") == COND_PASS.TWO) {
			if(condition.getOperator() == OPERATOR.EMPTY) {
				w(String.format("digitalRead(%d) == %s", condition.getSensor().getPin(), condition.getValue()));
			}
			else if (condition.getOperator() == OPERATOR.AND) {
				w(String.format(" && digitalRead(%d) == %s", condition.getSensor().getPin(), condition.getValue()));
			}
			else if (condition.getOperator() == OPERATOR.OR) {
				w(String.format(" || digitalRead(%d) == %s", condition.getSensor().getPin(), condition.getValue()));
			}
		}
		if(context.get("cond_pass") == COND_PASS.THREE) {
			String sensorName = condition.getSensor().getName();
			w(String.format(" && %sBounceGuard", sensorName));
		}
		if(context.get("cond_pass") == COND_PASS.FOUR) {
			String sensorName = condition.getSensor().getName();
			w(String.format("\t\t\t\t%sLastDebounceTime = millis();\n", sensorName));
		}
	}

	@Override
	public void visit(Action action) {
		if(context.get("pass") == PASS.ONE) {
			return;
		}
		if(context.get("pass") == PASS.TWO) {
			w(String.format("\t\t\tdigitalWrite(%d,%s);\n",action.getActuator().getPin(),action.getValue()));
        }
	}

}
