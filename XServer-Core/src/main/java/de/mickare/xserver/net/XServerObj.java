package de.mickare.xserver.net;

import java.io.IOException;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import de.mickare.xserver.AbstractXServerManager;
import de.mickare.xserver.AbstractXServerManagerObj;
import de.mickare.xserver.Message;
import de.mickare.xserver.XGroup;
import de.mickare.xserver.XType;
import de.mickare.xserver.events.XServerMessageOutgoingEvent;
import de.mickare.xserver.exceptions.NotInitializedException;
import de.mickare.xserver.util.Encryption;
import de.mickare.xserver.util.MyStringUtils;
import de.mickare.xserver.util.concurrent.CloseableLock;
import de.mickare.xserver.util.concurrent.CloseableReadWriteLock;
import de.mickare.xserver.util.concurrent.CloseableReentrantReadWriteLock;

public class XServerObj implements XServer {

	//private final static int MESSAGE_CACHE_SIZE = 8192;

	private final String name;
	private final String host;
	private final int port;
	private final String password;

	private volatile boolean deprecated = false;

	private Connection connection = null;
	private Connection connection2 = null; // Fix for HomeServer that is not
											// connectable.
	private CloseableReadWriteLock conLock = new CloseableReentrantReadWriteLock();

	private CloseableReadWriteLock typeLock = new CloseableReentrantReadWriteLock();
	private XType type = XType.Other;

	private final Set<XGroup> groups = new HashSet<XGroup>();

	//private final CacheList<Packet> pendingPackets = new CacheList<Packet>( MESSAGE_CACHE_SIZE );

	private final AbstractXServerManagerObj manager;

	public XServerObj(String name, String host, int port, String password, AbstractXServerManagerObj manager) {
		this.name = name;
		this.host = host;
		this.port = port;
		this.password = Encryption.MD5( password );
		this.manager = manager;
	}

	public XServerObj(String name, String host, int port, String password, XType type, AbstractXServerManagerObj manager) {
		this.name = name;
		this.host = host;
		this.port = port;
		this.password = Encryption.MD5( password );
		this.type = type;
		this.manager = manager;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see de.mickare.xserver.net.XServer#connect()
	 */
	@Override
	public void connect() throws UnknownHostException, IOException, InterruptedException, NotInitializedException {
		try (CloseableLock c = conLock.writeLock().open()) {
			if (!valid()) {
				return;
			}
			if (isConnected()) {
				this.disconnect();
			}
			new ConnectionObj( manager.getSocketFactory(), host, port, this, manager );
		}
	}

	public void setConnection( Connection con ) {
		try (CloseableLock c = conLock.writeLock().open()) {
			if (this.connection != con) {
				this.disconnect();
			}
			this.connection = con;
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see de.mickare.xserver.net.XServer#setReloginConnection(de.mickare.xserver.net.Connection)
	 */
	@Override
	public void setReloginConnection( Connection con ) {
		if (manager.getHomeServer() == this) {
			try (CloseableLock c = conLock.writeLock().open()) {
				if (this.connection2 != con && (this.connection2 != null ? this.connection2.isConnected() : false)) {
					this.disconnect();
				}
				this.connection2 = con;
			}
		} else {
			setConnection( con );
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see de.mickare.xserver.net.XServer#isConnected()
	 */
	@Override
	public boolean isConnected() {
		try {
			if(conLock.readLock().tryLock( 500, TimeUnit.MILLISECONDS )) {
				try {
					return connection != null ? connection.isLoggedIn() : false;
				} finally {
					conLock.readLock().unlock();
				}
			}
		} catch ( InterruptedException e ) {
		}
		return false;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see de.mickare.xserver.net.XServer#disconnect()
	 */
	@Override
	public void disconnect() {
		try (CloseableLock c = conLock.writeLock().open()) {
			if (connection != null) {
				connection.disconnect();
				/*
				synchronized (pendingPackets) {
					for (Packet p : this.connection.getPendingPackets()) {
						if (p.getPacketID() == PacketType.Message.packetID) {
							this.pendingPackets.push( p );
						}
					}
				}*/
				connection = null;
				connection2 = null;
			}
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see de.mickare.xserver.net.XServer#getName()
	 */
	@Override
	public String getName() {
		return name;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see de.mickare.xserver.net.XServer#getHost()
	 */
	@Override
	public String getHost() {
		return host;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see de.mickare.xserver.net.XServer#getPort()
	 */
	@Override
	public int getPort() {
		return port;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see de.mickare.xserver.net.XServer#getPassword()
	 */
	@Override
	public String getPassword() {
		return password;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see de.mickare.xserver.net.XServer#sendMessage(de.mickare.xserver.Message)
	 */
	@Override
	public boolean sendMessage( Message message ) throws IOException {
		boolean result = false;
		if (!valid()) {
			return false;
		}
		if (!isConnected()) {
			/*
			synchronized (pendingPackets) {
				pendingPackets.push( new Packet( PacketType.Message, message.getData() ) );
			}
			*/
			// throw new NotConnectedException("Not Connected to this server!");
		} else {
			try (CloseableLock c = conLock.readLock().open()) {
				if (connection.send( new Packet( PacketType.Message, message.getData() ) )) {
					result = true;
				}
			}
		}

		manager.getEventHandler().callEvent( new XServerMessageOutgoingEvent( this, message ) );
		return result;

	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see de.mickare.xserver.net.XServer#ping(de.mickare.xserver.net.Ping)
	 */
	@Override
	public void ping( Ping ping ) throws InterruptedException, IOException {
		try (CloseableLock c = conLock.readLock().open()) {
			if (isConnected()) {
				connection.ping( ping );
			}
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see de.mickare.xserver.net.XServer#flushCache()
	 */
	@Override
	public void flushCache() {
		/*
		try (CloseableLock c = conLock.readLock().open()) {
			if (isConnected()) {
				
				synchronized (pendingPackets) {
					Packet p = pendingPackets.pollLast();
					while (p != null) {
						connection.send( p );
						p = pendingPackets.pollLast();
					}
				}
				
			}
		}*/
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see de.mickare.xserver.net.XServer#getType()
	 */
	@Override
	public XType getType() {
		try (CloseableLock c = typeLock.readLock().open()) {
			return type;
		}
	}

	protected void setType( XType type ) {
		try (CloseableLock c = typeLock.writeLock().open()) {
			this.type = type;
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see de.mickare.xserver.net.XServer#getManager()
	 */
	@Override
	public AbstractXServerManager getManager() {
		return manager;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see de.mickare.xserver.net.XServer#getSendingRecordSecondPackageCount()
	 */
	@Override
	public long getSendingRecordSecondPackageCount() {
		try (CloseableLock c = conLock.readLock().open()) {
			if (isConnected()) {
				if (this.connection2 != null) {
					return this.connection.getSendingRecordSecondPackageCount() + this.connection2.getSendingRecordSecondPackageCount();
				}
				return this.connection.getSendingRecordSecondPackageCount();
			}
		}
		return 0;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see de.mickare.xserver.net.XServer#getSendinglastSecondPackageCount()
	 */
	@Override
	public long getSendinglastSecondPackageCount() {
		try (CloseableLock c = conLock.readLock().open()) {
			if (isConnected()) {
				if (this.connection2 != null) {
					return this.connection.getSendinglastSecondPackageCount() + this.connection2.getSendinglastSecondPackageCount();
				}
				return this.connection.getSendinglastSecondPackageCount();
			}
		}
		return 0;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see de.mickare.xserver.net.XServer#getReceivingRecordSecondPackageCount()
	 */
	@Override
	public long getReceivingRecordSecondPackageCount() {
		try (CloseableLock c = conLock.readLock().open()) {
			if (isConnected()) {
				if (this.connection2 != null) {
					return this.connection.getReceivingRecordSecondPackageCount() + this.connection2.getReceivingRecordSecondPackageCount();
				}
				return this.connection.getReceivingRecordSecondPackageCount();
			}
		}
		return 0;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see de.mickare.xserver.net.XServer#getReceivinglastSecondPackageCount()
	 */
	@Override
	public long getReceivinglastSecondPackageCount() {
		try (CloseableLock c = conLock.readLock().open()) {
			if (isConnected()) {
				if (this.connection2 != null) {
					return this.connection.getReceivinglastSecondPackageCount() + this.connection2.getReceivinglastSecondPackageCount();
				}
				return this.connection.getReceivinglastSecondPackageCount();
			}
		}
		return 0;
	}

	public void addGroup( XGroup g ) {
		this.groups.add( g );
	}

	@Override
	public Set<XGroup> getGroups() {
		return Collections.unmodifiableSet( this.groups );
	}

	@Override
	public boolean hasGroup( XGroup group ) {
		return this.groups.contains( group );
	}

	private boolean valid() {
		if (deprecated) {
			this.manager.getLogger().warning(
					"This XServer Object \"" + this.name + "\" is deprecated!\n"
							+ MyStringUtils.stackTraceToString( Thread.currentThread().getStackTrace() ) );
			return false;
		}

		return true;
	}

	@Override
	public boolean isDeprecated() {
		return this.deprecated;
	}

	public void setDeprecated() {
		this.deprecated = true;
	}

	@Override
	public XServer getCurrentXServer() {
		return this.manager.getXServer( name );
	}

}