/**
 * 
 */
package evaluate;

import java.io.File;

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

import com.sun.javafx.sg.prism.NGShape.Mode;

/**
 * @author stefano
 *
 */
public class Application {

	private static enum RelTypes implements RelationshipType {
		KNOWS
	}

	private static final String DB_PATH = "target/neo4j-hello-db";

	private static void deleteFileOrDirectory(File file) {
		if (file.exists()) {
			if (file.isDirectory()) {
				for (File child : file.listFiles()) {
					deleteFileOrDirectory(child);
				}
			}
			file.delete();
		}
	}

	public static void main(final String[] args) {
		Application hello = new Application();
		hello.deleteDb();
		hello.createDb();
		hello.analyseDb();
		hello.removeData();
		hello.shutDown();
	}

	private static void registerShutdownHook(final GraphDatabaseService graphDb) {
		// Registers a shutdown hook for the Neo4j instance so that it
		// shuts down nicely when the VM exits (even if you "Ctrl-C" the
		// running application).
		Runtime.getRuntime().addShutdownHook(new Thread() {
			@Override
			public void run() {
				graphDb.shutdown();
			}
		});
	}

	private Node firstNode;

	private GraphDatabaseService graphDb;

	private Relationship relationship;

	private Node secondNode;

	public String greeting;

	private void deleteDb() {
		deleteFileOrDirectory(new File(DB_PATH));
	}

	private void createDb() {
		graphDb = new GraphDatabaseFactory().newEmbeddedDatabase(DB_PATH);
		registerShutdownHook(graphDb);

		try (Transaction tx = graphDb.beginTx()) {
			// Database operations go here

			firstNode = graphDb.createNode();
			firstNode.setProperty("message", "Hello, ");
			secondNode = graphDb.createNode();
			secondNode.setProperty("message", "World!");

			relationship = firstNode.createRelationshipTo(secondNode, RelTypes.KNOWS);
			relationship.setProperty("message", "brave Neo4j ");

			System.out.print(firstNode.getProperty("message"));
			System.out.print(relationship.getProperty("message"));
			System.out.print(secondNode.getProperty("message"));

			greeting = ((String) firstNode.getProperty("message")) + ((String) relationship.getProperty("message"))
					+ ((String) secondNode.getProperty("message"));

			tx.success();
		}
	}

	private void analyseDb() {
		Transaction tx = graphDb.beginTx();
		try {
			PathFinder<Path> finder = GraphAlgoFactory.allPaths(//
					PathExpanders.forTypeAndDirection(RelTypes.KNOWS, Direction.BOTH), -1);
			Iterable<Path> paths = finder.findAllPaths(firstNode, secondNode);
			for (Path path : paths) {
				for (Node node : path.nodes()) {
					for (String prop : node.getPropertyKeys())
						System.out.print(prop);
					System.out.print(node.toString());
				}

				for (Relationship relationship : path.relationships())
					System.out.print(relationship.toString());
			}
			tx.success();
		} finally {
			tx.finish();
		}
	}

	private void removeData() {
		try (Transaction tx = graphDb.beginTx()) {
			// let's remove the data
			firstNode.getSingleRelationship(RelTypes.KNOWS, Direction.OUTGOING).delete();
			firstNode.delete();
			secondNode.delete();

			tx.success();
		}
	}

	private void shutDown() {
		System.out.println();
		System.out.println("Shutting down database ...");
		graphDb.shutdown();
	}

}
