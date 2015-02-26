package com.deering.humblebdd;

/**
 * Exception encountered by and throw during BDD operations.
 * @author tdeering
 *
 */
public class HumbleException extends RuntimeException {
	private static final long serialVersionUID = -3556271551729696994L;

	public HumbleException(){
	}
	
	public HumbleException(String msg){
		super(msg);
	}
	
	public HumbleException(Throwable t){
		super(t);
	}
	
	public HumbleException(String msg, Throwable t){
		super(msg, t);
	}
}