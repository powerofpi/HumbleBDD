package com.deering.humblebdd;

/**
 * Exception encountered by and throw during BDD operations.
 * @author tdeering
 *
 */
public class BDDException extends RuntimeException {
	private static final long serialVersionUID = -3556271551729696994L;

	public BDDException(){
	}
	
	public BDDException(String msg){
		super(msg);
	}
	
	public BDDException(Throwable t){
		super(t);
	}
	
	public BDDException(String msg, Throwable t){
		super(msg, t);
	}
}