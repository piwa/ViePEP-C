package at.ac.tuwien.infosys.viepepc.scheduler.core.impl.exceptions;

/**
 * 
 * @author Gerta Sheganaku
 *
 */
public class NoVmFoundException extends Throwable {
	private static final long serialVersionUID = 1L;

	public NoVmFoundException() { }

	public NoVmFoundException(String msg) {
		super(msg);
	}
}
