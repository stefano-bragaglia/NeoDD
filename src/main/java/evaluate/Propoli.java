/**
 * 
 */
package evaluate;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;

import org.neo4j.graphalgo.GraphAlgoFactory;
import org.neo4j.graphalgo.PathFinder;
import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Path;
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
public class Propoli {

	private static enum RelTypes implements RelationshipType {

		CHILD_HI, CHILD_LO;

	}

	public static final String DB_PATH = "target/neo4j-db";
	private static final String ID = "id";
	private static final String PROB = "prob";

	private static void delete(File file) {
		if (file.exists()) {
			if (file.isDirectory()) {
				for (File child : file.listFiles()) {
					delete(child);
				}
			}
			file.delete();
		}
	}

	public static void main(String[] args) {
		Propoli propoli = new Propoli(DB_PATH);
		propoli.delete();
		propoli.connect();
		propoli.traverse();
		propoli.populate();
		propoli.stamp();
		propoli.traverse();

		// TODO Auto-generated method stub

		System.out.println("Done.");
	}

	private GraphDatabaseService graph;

	private final String path;

	private Node one, root;

	public Propoli(String path) {
		if (null == path || (path = path.trim()).isEmpty())
			throw new IllegalArgumentException("Illegal 'path' argument in Propoli(String): " + path);
		this.path = path;
	}

	public GraphDatabaseService connect() {
		if (null == graph) {
			graph = new GraphDatabaseFactory().newEmbeddedDatabase(path);
			Runtime.getRuntime().addShutdownHook(new Thread() {
				@Override
				public void run() {
					graph.shutdown();
				}
			});
		}
		return graph;
	}

	public void delete() {
		delete(new File(path));
	}

	// (A) ----------[x2,0.5]----------> (b)
	// ................................. (b) --[x3,0.1]-> (C)
	// (A) --[x4,0.4]-> (d)
	// ................ (d) --[x5,0.5]-> (b)
	// ................................. (b) --[x3,0.1]-> (C)
	// P = 0.06 !!!

	public void populate() {
		if (null == graph)
			throw new IllegalStateException("Illegal state for 'graph' attribute in Propoli.populate(): the db is not connected");
		try (Transaction tx = graph.beginTx()) {
			Node zero, x2, x3, x4, x5;
			(zero = graph.createNode()).setProperty(ID, "0");
			(one = graph.createNode()).setProperty(ID, "1");

			(x2 = graph.createNode()).setProperty(ID, "2");
			(x3 = graph.createNode()).setProperty(ID, "3");
			(x4 = graph.createNode()).setProperty(ID, "4");
			(x5 = graph.createNode()).setProperty(ID, "5");

			Relationship relationship;
			(relationship = x3.createRelationshipTo(x2, RelTypes.CHILD_HI)).setProperty(PROB, 0.1);
			(relationship = x3.createRelationshipTo(zero, RelTypes.CHILD_LO)).setProperty(PROB, 0.9);
			(relationship = x2.createRelationshipTo(one, RelTypes.CHILD_HI)).setProperty(PROB, 0.5);
			(relationship = x2.createRelationshipTo(x4, RelTypes.CHILD_LO)).setProperty(PROB, 0.5);
			(relationship = x4.createRelationshipTo(x5, RelTypes.CHILD_HI)).setProperty(PROB, 0.4);
			(relationship = x4.createRelationshipTo(zero, RelTypes.CHILD_LO)).setProperty(PROB, 0.6);
			(relationship = x5.createRelationshipTo(one, RelTypes.CHILD_HI)).setProperty(PROB, 0.5);
			(relationship = x5.createRelationshipTo(zero, RelTypes.CHILD_LO)).setProperty(PROB, 0.5);

			root = x3;
			try {
				OutputStream out = new ByteArrayOutputStream();
				GraphvizWriter writer = new GraphvizWriter();
				writer.emit(out, Walker.fullGraph(graph));
				System.out.println(out.toString());
			} catch (IOException e) {
			}
			tx.success();
		}
	}

	public void traverse() {
		if (null != graph && null != root) {
			try (Transaction tx = graph.beginTx()) {
				PathFinder<Path> finder = GraphAlgoFactory.allPaths(
						PathExpanders.forTypesAndDirections(RelTypes.CHILD_HI, Direction.OUTGOING, RelTypes.CHILD_LO, Direction.OUTGOING), 5);
				// 5 is the number of original relationships + 1
				Iterable<Path> paths = finder.findAllPaths(root, one);
				double total = 0.0;
				for (Path path : paths) {
					double current = 1.0;
					for (Relationship relationship : path.relationships()) {
						current *= (double) relationship.getProperty(PROB, 1.0);
					}
					total += current;
				}
				System.out.format("The probability is %.2f.\n", total);
				tx.success();
			}
		}
	}

	public void stamp() {
		if (null != graph && null != root) {
			try (Transaction tx = graph.beginTx()) {
				PathFinder<Path> finder = GraphAlgoFactory.allPaths(
						PathExpanders.forTypesAndDirections(RelTypes.CHILD_HI, Direction.OUTGOING, RelTypes.CHILD_LO, Direction.OUTGOING), 5);
				// 5 is the number of original relationships + 1
				Iterable<Path> paths = finder.findAllPaths(root, one);
				for (Path path : paths) {
					boolean first = true;
					for (Relationship relationship : path.relationships()) {
						if (first) {
							System.out.format("(%s)", relationship.getStartNode().getProperty(ID));
							first = false;
						}

						System.out.format(" --[%s,%.2f]-> ", relationship.getType().name(), relationship.getProperty(PROB, 1.0));
						System.out.format("(%s)", relationship.getEndNode().getProperty(ID));
					}
					System.out.println();
				}
				tx.success();
			}
		}
	}

}
