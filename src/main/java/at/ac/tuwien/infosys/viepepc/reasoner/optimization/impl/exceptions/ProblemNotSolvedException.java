package at.ac.tuwien.infosys.viepepc.reasoner.optimization.impl.exceptions;

/**
 * 
 * @author Gerta Sheganaku
 *
 */
public class ProblemNotSolvedException extends Exception {
	private static final long serialVersionUID = 1L;

	public ProblemNotSolvedException() { }

	public ProblemNotSolvedException(String msg) {
		super(msg);
	}
}
