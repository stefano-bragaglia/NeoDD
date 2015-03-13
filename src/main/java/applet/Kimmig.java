/**
 * 
 */
package applet;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import org.neo4j.graphalgo.GraphAlgoFactory;
import org.neo4j.graphalgo.PathFinder;
import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Path;
import org.neo4j.graphdb.PathExpander;
import org.neo4j.graphdb.PathExpanders;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.RelationshipType;
import org.neo4j.graphdb.Transaction;
import org.neo4j.graphdb.factory.GraphDatabaseFactory;
import org.neo4j.visualization.graphviz.GraphvizWriter;
import org.neo4j.walk.Walker;

/**
 * @author stefano
 *
 */
public class Kimmig {

	private static enum RelType implements RelationshipType {
		EDGE;
	}

	private static final String DB_PATH = "target/neo4j-domain-db";

	private GraphDatabaseService graph;

	public Kimmig() {
		Utils.delete(DB_PATH);
		this.graph = new GraphDatabaseFactory().newEmbeddedDatabase(DB_PATH);
		this.nodes = new HashMap<>();
		Runtime.getRuntime().addShutdownHook(new Thread() {
			@Override
			public void run() {
				graph.shutdown();
			}
		});
	}

	private Map<String, Node> nodes;

	private int maxdepth = 1;

	public void add(String tail, String head, int logp) {
		if (null == tail || (tail = tail.trim()).isEmpty())
			throw new IllegalArgumentException(
					"Illegal 'tail' argument in Kimmig.add(String, String, int): "
							+ tail);
		if (null == head || (head = head.trim()).isEmpty())
			throw new IllegalArgumentException(
					"Illegal 'head' argument in Kimmig.add(String, String, int): "
							+ head);
		if (logp < 0)
			throw new IllegalArgumentException(
					"Illegal 'logp' argument in Kimmig.add(String, String, int): "
							+ logp);

		try (Transaction tx = graph.beginTx()) {

			Node start = nodes.get(tail);
			if (null == start) {
				start = graph.createNode();
				start.setProperty("name", tail);
				nodes.put(tail, start);
			}

			Node end = nodes.get(head);
			if (null == end) {
				end = graph.createNode();
				end.setProperty("name", head);
				nodes.put(head, end);
			}

			double prob = Math.exp(logp / -1000.0);

			boolean found = false;
			Iterator<Relationship> iterator = end.getRelationships(
					Direction.OUTGOING, RelType.EDGE).iterator();
			while (iterator.hasNext() && !found) {
				Relationship rel = iterator.next();
				found = rel.getEndNode().equals(start)
						&& rel.getProperty("prob").equals(prob);
			}
			if (!found) {
				Relationship rel = start
						.createRelationshipTo(end, RelType.EDGE);
				rel.setProperty("prob", prob);
				maxdepth += 1;
			}

			tx.success();
		}
	}

	public double path(String source, String target) {
		if (null == source || (source = source.trim()).isEmpty())
			throw new IllegalArgumentException(
					"Illegal 'source' argument in Kimmig.path(String, String): "
							+ source);
		if (null == target || (target = target.trim()).isEmpty())
			throw new IllegalArgumentException(
					"Illegal 'target' argument in Kimmig.path(String, String): "
							+ target);
		if (!nodes.containsKey(source) || !nodes.containsKey(target))
			return 0.0;
		Node start = nodes.get(source);
		Node end = nodes.get(target);
		if (start.equals(end))
			return 1.0;
		
		try (Transaction ignore = graph.beginTx()) {
			PathExpander<Object> expander = PathExpanders.forTypeAndDirection(
					RelType.EDGE, Direction.BOTH);
			PathFinder<Path> finder = GraphAlgoFactory.allPaths(expander,
					maxdepth);
			Iterable<Path> paths = finder.findAllPaths(start, end);
			if (!paths.iterator().hasNext())
				return -1.0;
			
			Set<Relationship> relationships = new HashSet<>();
			Set<Set<Relationship>> expression = new HashSet<>();
			for (Path path : paths) {
				Set<Relationship> item = new HashSet<>();
				for (Relationship relationship : path.relationships()) {
					relationships.add(relationship);
					item.add(relationship);
				}
				expression.add(item);
			}

			BDD bdd = new BDD(expression, relationships);
			// bdd.dump("bdd.gv");
			return bdd.traverse();
		}
	}

	public void dump(String path) {
		if (null == path || (path = path.trim()).isEmpty())
			throw new IllegalArgumentException("Illegal 'path' argument in Kimmig.dump(String): " + path);
		try (Transaction ignore = graph.beginTx()) {
			try {
				File file = new File(path);
				OutputStream out = new FileOutputStream(file);
				GraphvizWriter writer = new GraphvizWriter();
				writer.emit(out, Walker.fullGraph(graph));
			} catch (IOException e) {
				throw new IllegalArgumentException("Illegal 'path' argument in Kimmig.dump(String): " + path);
			}
		}
	}
	
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		long loading = System.nanoTime();
		Kimmig kimmig = new Kimmig();
		kimmig.add("hgnc_620", "entrezprotein_33339674", 663);
		kimmig.add("hgnc_9165", "pubmed_15214843", 504);
		kimmig.add("hgnc_621", "entrezprotein_3342564", 435);
		kimmig.add("uniprot_o00213", "go_go_0001540", 672);
		kimmig.add("hgnc_983", "pubmed_2322535", 103);
		kimmig.add("hgnc_620", "pubmed_9136074", 843);
		kimmig.add("uniprot_o75882", "pubmed_14760718", 747);
		kimmig.add("hgnc_2313", "pubmed_14760718", 627);
		kimmig.add("hgnc_1358", "pubmed_14760718", 749);
		kimmig.add("hgnc_5394", "entrezprotein_182607", 395);

		kimmig.add("hgnc_9087", "pubmed_7622043", 347);
		kimmig.add("hgnc_983", "pubmed_1769657", 103);
		kimmig.add("hgnc_620", "pubmed_2507928", 103);

		kimmig.add("hgnc_2313", "entrezprotein_27769064", 297);

		loading = System.nanoTime() - loading;
		kimmig.dump("kimmig.gv");
		long solving = System.nanoTime();
		double prob = kimmig.path("hgnc_620", "hgnc_983");

		solving = System.nanoTime() - solving;
		System.out.format("%d,%.3f,%.3f,%.3f\n", 1, loading / 1_000_000_000.0,
				solving / 1_000_000_000.0, prob);
		System.out.println("Done.");
	}
}
