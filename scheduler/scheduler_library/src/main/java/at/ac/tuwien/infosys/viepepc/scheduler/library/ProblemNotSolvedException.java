package at.ac.tuwien.infosys.viepepc.scheduler.library;

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
