package org.lantern;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.lang3.StringUtils;
import org.codehaus.jackson.JsonParseException;
import org.codehaus.jackson.map.JsonMappingException;
import org.codehaus.jackson.map.ObjectMapper;
import org.lantern.state.Mode;
import org.lantern.state.Model;
import org.lantern.state.ModelUtils;
import org.lantern.state.Peer;
import org.lantern.state.Peer.Type;
import org.lantern.state.Peers;
import org.lantern.util.LanternTrafficCounterHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Inject;
import com.google.inject.Singleton;

@Singleton
public class PeerFactory {

    private final Logger log = LoggerFactory.getLogger(getClass());

    private final ModelUtils modelUtils;
    private final Peers peers;

    /**
     * We create an executor here because we need to thread our geo-ip lookups.
     */
    private final ExecutorService exec = Executors.newCachedThreadPool(
        new ThreadFactory() {

        private final AtomicInteger count = new AtomicInteger();
        @Override
        public Thread newThread(final Runnable runner) {
            final Thread t = new Thread(runner, "Peer-Factory-Thread-"+count);
            t.setDaemon(true);
            count.incrementAndGet();
            return t;
        }
    });

    private final Roster roster;

    @Inject
    public PeerFactory(final ModelUtils modelUtils, final Model model,
            final Roster roster) {
        this.modelUtils = modelUtils;
        this.roster = roster;
        this.peers = model.getPeerCollector();
    }

    public void addPeer(final String userId, final InetAddress address, 
        final int port, 
        final Type type, final boolean incoming, 
        final LanternTrafficCounterHandler trafficCounter) {
        exec.submit(new Runnable() {

            @Override
            public void run() {
                log.debug("Adding peer");
                final Peer existing;
                if (StringUtils.isNotBlank(userId)) {
                    existing = peers.getPeer(userId);
                } else {
                    final InetSocketAddress key = 
                            new InetSocketAddress(address, port);
                    existing = peers.getPeer(key);

                }
                if (existing != null) {
                    log.debug("Peer already exists...");
                    
                    // It could have just been deserialized from disk, so we
                    // want to give it a real traffic counter.
                    final LanternTrafficCounterHandler tc = 
                        existing.getTrafficCounter();
                    if (tc != null) {
                        log.warn("Existing traffic counter?");
                    } else {
                        log.debug("Adding traffic counter...");
                        existing.setTrafficCounter(trafficCounter);
                    }
                } else {
                    final Peer peer = newGiveModePeer(userId, address, port, type, 
                            incoming, trafficCounter);
                    peers.addPeer(new InetSocketAddress(address, port), peer);
                }
                //Events.sync(SyncPath.PEERS, peers.getPeers());
            }

        });
    }
    
    private Peer newGiveModePeer(final String userId, final InetAddress address, 
        final int port, final Type type, final boolean incoming, 
        final LanternTrafficCounterHandler trafficCounter) {
        
        final LanternRosterEntry entry;
        if (StringUtils.isNotBlank(userId)) {
            final LanternRosterEntry temp = this.roster.getRosterEntry(userId);
            if (temp != null) {
                entry = temp;
            } else {
                entry = new LanternRosterEntry();
            }
        } else {
            entry = new LanternRosterEntry();
        }
        

        //final LanternRosterEntry finalEntry = copyEntry(entry);

        final GeoData geo = modelUtils.getGeoData(address.getHostAddress());
        return new Peer(userId, geo.getCountrycode(), true, geo.getLatitude(), 
            geo.getLongitude(), type, address.getHostAddress(), Mode.give, 
            incoming, trafficCounter, entry);
    }

    private LanternRosterEntry copyEntry(LanternRosterEntry entry) {
        final String json = JsonUtils.jsonify(entry);
        final ObjectMapper om = new ObjectMapper();
        try {
            return om.readValue(json, LanternRosterEntry.class);
        } catch (JsonParseException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (JsonMappingException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return new LanternRosterEntry();
    }

    /*
    public Peer newPeer(final String userId, final Type type) {
        final GeoData geo = modelUtils.getGeoData(ip);
        return new Peer(userId, geo.getCountrycode(), false, geo.getLatitude(),
            geo.getLongitude(), type);
    }
    */
}