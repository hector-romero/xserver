package com.mickare.xserver.net;

import java.io.IOException;
import java.util.Collection;
import java.util.Queue;

public interface Connection {

	public enum STATS
	{
		disconnected, connecting, connected, error
	}
	
	public abstract void ping(Ping ping) throws InterruptedException,
			IOException;

	public abstract boolean isConnected();

	public abstract void disconnect();
	
	public abstract void errorDisconnect();

	public abstract void errorDisconnect(STATS errorstatus);
	
	public abstract String getHost();

	public abstract int getPort();

	public abstract boolean send(Packet packet);

	public abstract boolean sendAll(Collection<Packet> packets);

	public abstract STATS getStatus();

	public abstract XServer getXserver();

	public abstract Queue<Packet> getPendingPackets();

	public abstract boolean isLoggedIn();

	public abstract boolean isLoggingIn();

	public abstract String toString();

	public abstract long getSendingRecordSecondPackageCount();

	public abstract long getSendinglastSecondPackageCount();

	public abstract long getReceivingRecordSecondPackageCount();

	public abstract long getReceivinglastSecondPackageCount();

}