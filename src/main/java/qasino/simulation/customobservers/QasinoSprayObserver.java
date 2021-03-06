package qasino.simulation.customobservers;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import org.apache.jena.graph.Triple;
import org.apache.jena.sparql.core.Var;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import peersim.config.Configuration;
import peersim.core.CommonState;
import peersim.core.Network;
import qasino.simulation.observers.DictGraph;
import qasino.simulation.observers.ObserverProgram;
import qasino.simulation.qasino.Datastore;
import qasino.simulation.qasino.Qasino;

import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Stream;

import static java.lang.System.exit;

public class QasinoSprayObserver implements ObserverProgram {
    private final int begin = 50;
    private int query = 73;

    private JSONObject queryToreplicate = null;


    // can be "lasvegas", end when all have seen all, or "montecarlo", if montecarlo, proportion=0.999999999 until the end of the experiment
    private String stopcond = "lasvegas";
    private double proportion = 0.99999;
    private double montecarlostop = 0;
    private int replicate;
    private int queries;
    private boolean initialized = false;

    private Map<Long, Qasino> collaborativepeers = new LinkedHashMap<>();

    public QasinoSprayObserver(String prefix) {
        try {
            this.stopcond = Configuration.getString(prefix + ".stopcond", "lasvegas");
            this.query = Configuration.getInt(prefix + ".querytoreplicate", 73);
            this.replicate = Configuration.getInt(prefix + ".replicate", 50);
        } catch (Exception e) {
            System.err.println("Cant find any query limit: setting value to unlimited: " + e);
        }
        this.montecarlostop = Network.size() * Math.log((1 / (1 - proportion)));

    }

    public static Map<String, Object> jsonToMap(JSONObject json) {
        Map<String, Object> retMap = new HashMap<String, Object>();

        if (json != null) {
            retMap = toMap(json);
        }
        return retMap;
    }

    public static Map<String, Object> toMap(JSONObject object) {
        Map<String, Object> map = new HashMap<String, Object>();

        Iterator<String> keysItr = object.keySet().iterator();
        while (keysItr.hasNext()) {
            String key = keysItr.next();
            Object value = object.get(key);

            if (value instanceof JSONArray) {
                value = toList((JSONArray) value);
            } else if (value instanceof JSONObject) {
                value = toMap((JSONObject) value);
            }
            map.put(key, value);
        }
        return map;
    }

    public static List<Object> toList(JSONArray array) {
        List<Object> list = new ArrayList<Object>();
        for (int i = 0; i < array.size(); i++) {
            Object value = array.get(i);
            if (value instanceof JSONArray) {
                value = toList((JSONArray) value);
            } else if (value instanceof JSONObject) {
                value = toMap((JSONObject) value);
            }
            list.add(value);
        }
        return list;
    }

    @Override
    public void tick(long currentTick, DictGraph observer) {
        if (currentTick == begin) {
            init(observer);
            System.err.println(new GsonBuilder().setPrettyPrinting().create().toJson(new JsonParser().parse(queryToreplicate.toJSONString())));
            initialized = true;
            Qasino.start = true;
        } else {
            if (initialized) observe(currentTick, observer);
        }
    }

    public void observe(long currentTick, DictGraph observer) {
        // always pick the first collaborative peer.
        Map.Entry<Long, Qasino> entry = collaborativepeers.entrySet().iterator().next();
        Qasino peer = entry.getValue();
        Map<String, Object> data = jsonToMap((JSONObject) queryToreplicate.get("patterns"));
        System.out.println(String.join(",", new String[]{
                String.valueOf(peer.node.getID()),
                String.valueOf(peer.shuffle),
                String.valueOf(peer.observed),
                String.valueOf(peer.profile.query.globalseen),
                String.valueOf(peer.crdt.sum()),
                String.valueOf(peer.messages),
                String.valueOf(peer.tripleResponses),
                String.valueOf(peer.profile.local_datastore.inserted),
                String.valueOf(peer.profile.query.getResults().size()),
                String.valueOf(peer.profile.query.cardinality),
                String.valueOf((double) peer.profile.query.getResults().size() / peer.profile.query.cardinality),
                String.valueOf(Network.size()),
                String.valueOf(peer.getPeers(Integer.MAX_VALUE).size()),
                String.valueOf(replicate),
                String.valueOf(Qasino.traffic),
                String.valueOf(query)
        }));

        boolean shouldexit = true;
        for (Map.Entry<Long, Qasino> end : collaborativepeers.entrySet()) {
            // should stop the query
            switch (stopcond) {
                case "lasvegas":
                    lasVegasCondition(end.getValue());
                    break;
                case "montecarlo":
                    monteCarloCondition(end.getValue());
                    break;
                case "both":
                    bothCondition(end.getValue());
                    break;
                default:
                    System.err.println("No termination defined! switch to las vegas termination");
                    lasVegasCondition(end.getValue());
            }
            if (end.getKey() == peer.node.getID()) {
                // should exit
                shouldexit = shouldexit && end.getValue().profile.query.terminated;
            }
        }
        if (shouldexit) {
            exit(0);
        }
    }

    private void bothCondition(Qasino peer) {
        if (peer.profile.has_query && peer.crdt.sum() > montecarlostop && peer.profile.query.isFinished(peer.getEstimator())) {
            peer.profile.stop();
        }
    }

    private void monteCarloCondition(Qasino peer) {
        if (peer.profile.has_query && peer.crdt.sum() > montecarlostop) {
            peer.profile.stop();
        }
    }

    private void lasVegasCondition(Qasino peer) {
        if (peer.profile.has_query && peer.profile.query.isFinished(peer.getEstimator())) {
            peer.profile.stop();
        }
    }

    @Override
    public void onLastTick(DictGraph observer) {

    }

    public void init(DictGraph observer) {
        Datastore d = new Datastore();
        // hack to get the proper pid.... fix it for a proper version
        int networksize = Network.size();
        System.err.println("[INIT:SNOB-SPRAY] Initialized data for: " + networksize + " peers..." + observer.nodes.size());
        String diseasome = System.getProperty("user.dir") + "/datasets/data/diseasome/fragments/";
        System.err.println(System.getProperty("user.dir"));
        String diseasomeQuery = System.getProperty("user.dir") + "/datasets/data/diseasome/queries/queries_jena_generated.json";
        Vector filenames = new Vector();
        try (Stream<Path> paths = Files.walk(Paths.get(diseasome))) {
            paths.filter(Files::isRegularFile).forEach((fileName) -> filenames.add(fileName));
        } catch (IOException e) {
            System.err.println(e.toString());
        }
        System.err.println("[INIT:SNOB-SPRAY] Number of fragments to load: " + filenames.size());
        for (Object filename : filenames) {
            d.update(filename.toString());
        }

        Vector<Qasino> peers = new Vector();
        for (int i = 0; i < networksize; ++i) {
            Qasino snob = (Qasino) (observer.nodes.get(Network.get(i).getID()).pss);
            peers.add(snob);
        }

        // now create the construct query
        Triple spo = new Triple(Var.alloc("s"), Var.alloc("p"), Var.alloc("o"));
        List<Triple> result = d.getTriplesMatchingTriplePatternAsList(spo);
        Collections.shuffle(result, CommonState.r);
        int k = 0;
        Iterator<Triple> it = result.iterator();
        while (it.hasNext()) {
            Triple triple = it.next();
            List<Triple> list = new LinkedList<>();
            list.add(triple);
            peers.get(k % peers.size()).profile.local_datastore.insertTriples(list);
            k++;
        }
        JSONParser parser = new JSONParser();
        Vector<JSONObject> queriesDiseasome = new Vector();
        try (Reader is = new FileReader(diseasomeQuery)) {
            JSONArray jsonArray = (JSONArray) parser.parse(is);
            jsonArray.stream().forEach((q) -> {
                JSONObject j = (JSONObject) q;
                queriesDiseasome.add(j);
            });

        } catch (IOException | ParseException e) {
            e.printStackTrace();
        }

        // create a vector containing all queries where queries are inserted one after the other respectively from each dataset
        Vector<JSONObject> finalQueries = new Vector();
        for (int i = 0; i < queriesDiseasome.size(); i++) {
            finalQueries.add(queriesDiseasome.get(i));
        }

        // now check the replicate factor and replicate a random query.
        JSONObject queryToreplicate = finalQueries.get(this.query); // finalQueries.get((int) Math.floor(Math.random() * queriesDiseasome.size()));
        int numberOfReplicatedQueries = this.replicate;
        this.queryToreplicate = queryToreplicate;

        // pick peer that will receive queries
        List<Qasino> nodes = new ArrayList<>();
        Random random = new Random(CommonState.r.getLastSeed());
        while (nodes.size() != numberOfReplicatedQueries) {
            int rn = (int) Math.floor(random.nextDouble() * Network.size());
            Qasino n = (Qasino) observer.nodes.get(Network.get(rn).getID()).pss;
            if (!nodes.contains(n)) nodes.add(n);
        }

        this.queries = numberOfReplicatedQueries;
        for (int i = 0; i < nodes.size(); ++i) {
            Qasino snob = nodes.get(i);
            collaborativepeers.put(snob.node.getID(), snob);
            System.err.println("Add a new collaborative peer: " + snob.node.getID());
            snob.profile.replicate = numberOfReplicatedQueries;
            snob.profile.update((String) queryToreplicate.get("query"), (long) queryToreplicate.get("card"));
        }
    }
}
