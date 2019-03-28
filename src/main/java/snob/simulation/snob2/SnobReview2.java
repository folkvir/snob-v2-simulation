package snob.simulation.snob2;

import org.apache.jena.graph.Triple;
import peersim.config.Configuration;
import peersim.core.CommonState;
import peersim.core.Network;
import peersim.core.Node;
import snob.simulation.rps.ARandomPeerSamplingProtocol;
import snob.simulation.rps.IMessage;
import snob.simulation.rps.IRandomPeerSampling;
import snob.simulation.snob2.data.IBFStrata;

import java.util.*;

/**
 * The Snob protocol
 */
public class SnobReview2 extends Snob {
    // #A the names of the parameters in the configuration file of peersim
    private static final String PAR_C = "c"; // max partial view size
    private static final String PAR_L = "l"; // shuffle size
    private static final String PAR_SON = "son"; // enable son or not
    private static final String PAR_TRAFFIC = "traffic"; // minimization of the traffic
    public static boolean start = false;
    // #B the values from the configuration file of peersim
    public static int c;
    public static int l;
    public static boolean son;
    public static int RND_WALK = 5;
    public static boolean traffic;
    public static int pick = 5;
    private static int snobs = 0;
    public final int id = SnobReview2.snobs++;
    // Profile of the peer
    public Profile profile;
    // #C local variables
    public SnobPartialView partialView;
    public String prefix;
    // instrumentations
    public double messages = 0;
    public double messagesFullmesh = 0;
    public long tripleResponses = 0;
    public Set<Node> fullmesh = new LinkedHashSet<Node>();

    /**
     * Construction of a Snob instance, By default it is a Cyclon implementation wihtout using the overlay
     *
     * @param prefix the peersim configuration
     */
    public SnobReview2(String prefix) {
        super(prefix);
        this.prefix = prefix;
        SnobReview2.pick = Configuration.getInt(prefix + ".pick");
        SnobReview2.c = Configuration.getInt(prefix + "." + PAR_C);
        SnobReview2.l = Configuration.getInt(prefix + "." + PAR_L);
        SnobReview2.son = Configuration.getBoolean(prefix + "." + PAR_SON);
        SnobReview2.traffic = Configuration.getBoolean(prefix + "." + PAR_TRAFFIC);
        this.partialView = new SnobPartialView(SnobReview2.c, SnobReview2.l);
        try {
            this.profile = new Profile();
        } catch (Exception e) {
            System.err.println(e);
        }
    }

    /**
     * Perform a random walk in the network at a depth set by ttl (time-to-live)
     *
     * @param origin  the subscribing peer
     * @param current the current peer that either accept the subcription or
     *                forwards it
     * @param ttl     the current time-to-live before the subscription gets accepted
     */
    private static void randomWalk(Node origin, Node current, int ttl) {
        final SnobReview2 originSnob = (SnobReview2) origin.getProtocol(SnobReview2.pid);
        final SnobReview2 currentSnob = (SnobReview2) current.getProtocol(SnobReview2.pid);
        List<Node> aliveNeighbors = currentSnob.getAliveNeighbors();
        ttl -= 1;
        // #A if the receiving peer has neighbors in its partial view
        if (aliveNeighbors.size() > 0) {
            // #A1 if the ttl is greater than 0, continue the random walk
            if (ttl > 0) {
                final Node next = aliveNeighbors.get(CommonState.r
                        .nextInt(aliveNeighbors.size()));
                randomWalk(origin, next, ttl);
            } else {
                // #B if the ttl is greater than 0 or the partial view is empty,
                // then
                // accept the subscription and stop forwarding it
                if (origin.getID() != current.getID()) {
                    Iterator<Node> iPeers = currentSnob.getPeers(1)
                            .iterator();
                    if (iPeers.hasNext()) {
                        Node chosen = iPeers.next();
                        currentSnob.partialView.removeNode(chosen);
                        originSnob.partialView.addNeighbor(chosen);
                    }
                    currentSnob.addNeighbor(origin);
                }
            }
        }
    }

    public static SnobReview2 fromNodeToSnob(Node node) {
        return (SnobReview2) node.getProtocol(ARandomPeerSamplingProtocol.pid);
    }

    public void periodicCall() {
        // periodicRandomGraphCall();
        periodicCyclonCall();
    }

    /**
     * Construct the neighborhood using the RPS
     */
    public void periodicRandomGraphCall() {
        if (this.isUp() && this.partialView.size() > 0) {
            this.partialView.clear();
            List<Node> rps = new LinkedList<>();
            while (rps.size() != SnobReview2.pick) {
                int rn = (int) Math.floor(Math.random() * Network.size());
                Node randomNode = Network.get(rn);
                if (!rps.contains(randomNode) && !randomNode.equals(this.node)) {
                    rps.add(randomNode);
                    this.partialView.addNeighbor(randomNode);
                }
            }
            // son
            if (start) {
                if (SnobReview2.son) constructFullmesh(rps);
                // -------- QUERY EXECUTION MODEL -------
                if (profile.has_query && !profile.query.terminated) {
                    // 1 - send tpqs to neighbours and receive responses
                    List<Node> rps_neigh = rps;
                    for (Node node_rps : rps_neigh) {
                        this.exchangeTriplePatterns((SnobReview2) node_rps.getProtocol(ARandomPeerSamplingProtocol.pid), true);
                    }
                    profile.execute();
                    // test if the query is terminated or not
                    if (this.profile.query.globalseen == Network.size()) {
                        this.profile.query.stop();
                    }
                }
            }
        }
    }

    /**
     * Construct the fullmesh for the specified list of nodes
     *
     * @param rps
     */
    public void constructFullmesh(List<Node> rps) {

        rps.forEach(peer -> {
            if (!fullmesh.contains(peer) && this.profile.has_query && fromNodeToSnob(peer).profile.has_query && this.profile.query.query.equals(fromNodeToSnob(peer).profile.query.query)) {
                SnobReview2 remote = fromNodeToSnob(peer);
                // merge fullmesh
                fullmesh.addAll(remote.fullmesh);
                fullmesh.add(this.node);
                fullmesh.add(remote.node);

                // synchronize data everywhere
                // synchronize fullmesh data
                // firstly pull
                for (Node n : fullmesh) {
                    if (!n.equals(this.node)) {
                        // System.err.printf("Pull (%d) <- (%d) %n", this.id, fromNodeToSnob(n).id);
                        this.exchangeTriplePatterns(fromNodeToSnob(n), false);
                    }
                }
                for (Node n : fullmesh) {
                    if (!n.equals(this.node)) {
                        // System.err.printf("Push (%d) -> (%d) %n", this.id, fromNodeToSnob(n).id);
                        fromNodeToSnob(n).exchangeTriplePatterns(this, false);
                    }
                }
                LinkedHashSet<Node> us = new LinkedHashSet<>();
                for (Node p : fullmesh) {
                    if (p.equals(this.node)) {
                        us.addAll(fullmesh);
                        us.remove(p);
                    } else {
                        fromNodeToSnob(p).fullmesh.addAll(fullmesh);
                        fromNodeToSnob(p).fullmesh.remove(p);
                    }
                }
                fullmesh = us;
            }
        });
    }

    /**
     * Periodic call for the Cyclon Network
     */
    @Override
    public void periodicCyclonCall() {
//        messages = 0;
//        tripleResponses = 0;
        // do the periodic shuffling of the rps
        if (this.isUp() && this.partialView.size() > 0) {
            // do the periodic shuffling for Cyclon
            this.partialView.incrementAge();
            Node q = this.partialView.getOldest();
            SnobReview2 qSnob = fromNodeToSnob(q);
            if (qSnob.isUp() && !this.pFail(null)) {
                // #A if the chosen peer is alive, initiate the exchange
                List<Node> sample = this.partialView.getSample(this.node, q, true);
                sample.add(this.node);
                IMessage received = qSnob.onPeriodicCall(this.node, new SnobMessage(sample));
                List<Node> samplePrime = (List<Node>) received.getPayload();
                this.partialView.mergeSample(this.node, q, samplePrime, sample, true);
            } else {
                // #B if the chosen peer is dead, remove it from the view
                this.partialView.removeNode(q);
            }


            if (start) {

                // list the rps, check for Snob that are not in our fullmesh son, add it
                List<Node> all = this.getPeers(Integer.MAX_VALUE);
                List<Node> rps = new LinkedList<>();
                while (rps.size() < pick && !all.isEmpty()) {
                    int rn = (int) Math.floor(Math.random() * all.size());
                    if (!rps.contains(all.get(rn))) {
                        Node node = all.get(rn);
                        rps.add(node);
                        all.remove(node);
                    }
                }
                if (SnobReview2.son) constructFullmesh(rps);
                // -------- QUERY EXECUTION MODEL -------
                if (profile.has_query && !profile.query.isFinished()) {
                    // 1 - send tpqs to neighbours and receive responses
                    for (Node node_rps : rps) {
                        // System.err.println(this.id +" exchange with " + fromNodeToSnob(node_rps).id);
                        this.exchangeTriplePatterns(fromNodeToSnob(node_rps), true);
                        // algo 1
//                        if (fromNodeToSnob(node_rps).profile.has_query) {
//                            fromNodeToSnob(node_rps).exchangeTriplePatterns(this, true);
//                        }
                    }
                    profile.execute();
                    // test if the query is terminated or not
                    if (this.profile.query.globalseen == Network.size()) {
                        this.profile.query.stop();
                    }
                }
            }
        }
    }

    /**
     * Exchange Triple patterns with Invertible bloom filters associated with.
     * In return the other peers send us missing triples for each triple pattern.
     * See onTpqs(...) function
     *
     * @param remote
     */
    protected void exchangeTriplePatterns(SnobReview2 remote, boolean share) {
        this.profile.query.patterns.forEach(pattern -> {
            if (traffic) {
                exchangeTriplesFromPatternUsingIbf(remote, pattern, share);
            } else {
                this.messages++;
                List<Triple> list = remote.profile.datastore.getTriplesMatchingTriplePatternAsList(pattern);
                tripleResponses += list.size();
                this.profile.insertTriplesWithList(pattern, list);
                if (share) this.shareTriples(list, pattern);
            }

            // the algo one is just this!
            this.profile.query.addAlreadySeen(pattern, remote.id, this.id);
//            if (remote.profile.has_query && remote.profile.query.patterns.contains(pattern)) {
//                int before = this.profile.query.alreadySeen.get(pattern).size();
//                this.profile.query.mergeAlreadySeen(pattern, remote.profile.query.alreadySeen.get(pattern));
//                int after = this.profile.query.alreadySeen.get(pattern).size();
//                if (SnobReview2.son) {
//                    if (after - before > 0) {
//                        // System.err.println("Update the fullmesh with new pair");
//                        // considering that we provide this information by sharing during triple pattern exchange.
//                        for (Node node : fullmesh) {
//                            fromNodeToSnob(node).profile.query.mergeAlreadySeen(pattern, this.profile.query.alreadySeen.get(pattern));
//                        }
//                    }
//                }
//            }
        });
    }

    /**
     * Share triples with the fullmesh
     *
     * @param list
     * @param pattern
     */
    public void shareTriples(List<Triple> list, Triple pattern) {
        if (this.isUp && SnobReview2.son) {
            if (list.size() != 0) {
                // System.err.println("We have " + list.size() + "triples to share.");
                // share knowledge into the full mesh network
                fullmesh.forEach(node -> {
                    SnobReview2 remote = fromNodeToSnob(node);
                    if (traffic) {
                        IBFStrata localibf = IBFStrata.createIBFFromTriples(list);
                        IBFStrata.Result res = remote.profile.query.strata.get(pattern).difference(localibf);
                        if(res.messagessent == 0) {
                            this.messages++;
                            this.messagesFullmesh++;
                        } else {
                            this.messages += res.messagessent;
                            this.messagesFullmesh += res.messagessent;
                        }
                        remote.tripleResponses += remote.profile.insertTriplesWithList(pattern, res.missing);
                    } else {
                        remote.tripleResponses += remote.profile.insertTriplesWithList(pattern, this.profile.datastore.getTriplesMatchingTriplePatternAsList(pattern));
                        this.messages++;
                        this.messagesFullmesh++;
                    }
                });
            }
        }
    }

    /**
     * Exchange triple pattern using Invertible bloom filter, and share data if necessary
     *
     * @param remote
     * @param pattern
     */
    public void exchangeTriplesFromPatternUsingIbf(SnobReview2 remote, Triple pattern, boolean share) {
        // send the ibf to remote peer with the pattern
        if (!remote.profile.has_query || (remote.profile.has_query && !remote.profile.query.patterns.contains(pattern))) {
            // if remote does not have a query, get triple pattern, get ibf on this triple pattern, set reconciliation.
            List<Triple> computed = remote.profile.datastore.getTriplesMatchingTriplePatternAsList(pattern);
            if (computed.size() == 0) {
                this.messages++; // at least one message
            } else {
                IBFStrata remoteIbf = IBFStrata.createIBFFromTriples(computed);
                IBFStrata.Result res = this.profile.query.strata.get(pattern).difference(remoteIbf);
                if(res.messagessent == 0) {
                    this.messages++;
                } else {
                    this.messages += res.messagessent;
                }
                int t = this.profile.insertTriplesWithList(pattern, res.missing);
                this.tripleResponses += t;
                if (share) this.shareTriples(res.missing, pattern);
            }
        } else {
            // the remote peer has the pattern
            IBFStrata.Result res = this.profile.query.strata.get(pattern).difference(remote.profile.query.strata.get(pattern));
            if(res.messagessent == 0) {
                this.messages++;
            } else {
                this.messages += res.messagessent;
            }
            int t = this.profile.insertTriplesWithList(pattern, res.missing);
            this.tripleResponses += t;
            if (share) this.shareTriples(res.missing, pattern);
        }
    }

    @Override
    protected boolean pFail(List<Node> path) {
        // the probability is constant since the number of hops to establish
        // a connection is constant
        double pf = 1 - Math.pow(1 - ARandomPeerSamplingProtocol.fail, 6);
        return CommonState.r.nextDouble() < pf;
    }

    public IMessage onPeriodicCall(Node origin, IMessage message) {
        List<Node> samplePrime = this.partialView.getSample(this.node, origin,
                false);
        this.partialView.mergeSample(this.node, origin,
                (List<Node>) message.getPayload(), samplePrime, false);

        return new SnobMessage(samplePrime);
    }

    public void join(Node joiner, Node contact) {
        if (this.node == null) { // lazy loading of the node identity
            this.node = joiner;
        }
        if (contact != null) { // the very first join does not have any contact
            SnobReview2 contactSnob = (SnobReview2) contact.getProtocol(SnobReview2.pid);
            this.partialView.clear();
            this.partialView.addNeighbor(contact);
            contactSnob.onSubscription(this.node);
        }
        this.isUp = true;
    }

    public void onSubscription(Node origin) {
        List<Node> aliveNeighbors = this.getAliveNeighbors();
        Collections.shuffle(aliveNeighbors, CommonState.r);
        int nbRndWalk = Math.min(SnobReview2.c - 1, aliveNeighbors.size());

        for (int i = 0; i < nbRndWalk; ++i) {
            randomWalk(origin, aliveNeighbors.get(i), SnobReview2.RND_WALK);
        }
    }

    public void leave() {
        this.isUp = false;
        this.partialView.clear();
        // nothing else
    }

    public List<Node> getPeers(int k) {
        return this.partialView.getPeers(k);
    }

    @Override
    public IRandomPeerSampling clone() {
        try {
            SnobReview2 s = new SnobReview2(this.prefix);
            s.partialView = (SnobPartialView) this.partialView.clone();
            return s;
        } catch (CloneNotSupportedException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public boolean addNeighbor(Node peer) {
        return this.partialView.addNeighbor(peer);
    }

}