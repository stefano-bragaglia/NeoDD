/**
 * 
 */
package evaluate;

import java.io.File;

import org.neo4j.graphalgo.GraphAlgoFactory;
import org.neo4j.graphalgo.PathFinder;
import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.DynamicLabel;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Label;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Path;
import org.neo4j.graphdb.PathExpanders;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.RelationshipType;
import org.neo4j.graphdb.Transaction;
import org.neo4j.graphdb.factory.GraphDatabaseFactory;

/**
 * @author stefano
 *
 */
public class BiDiDi {

	private static enum RelTypes implements RelationshipType {
		
		LINKS("links");
		
		private String name;

		private RelTypes(String name) {
			this.name = name;
		}

		@Override
		public String toString() {
			return name;
		}
		
	}

	public static final String DB_PATH = "target/neo4j-db";
	private static final String ID = "id";
	private static final Label LABEL = DynamicLabel.label("ID");

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
		BiDiDi propoli = new BiDiDi(DB_PATH);
		propoli.delete();
		propoli.connect();
		propoli.stamp();
		propoli.populate();
		propoli.stamp();

		// TODO Auto-generated method stub

		System.out.println("Done.");
	}

	private GraphDatabaseService graph;

	private final String path;

	private Node root, done;

	public BiDiDi(String path) {
		if (null == path || (path = path.trim()).isEmpty())
			throw new IllegalArgumentException("Illegal 'path' argument in Propoli(String): " + path);
		this.path = path;
	}

	public GraphDatabaseService connect() {
		if (null == graph) {
			graph = new GraphDatabaseFactory().newEmbeddedDatabase(path);
			try (Transaction tx = graph.beginTx()) {
				graph.schema().indexFor(LABEL).on(ID).create();
				tx.success();
			}
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

	public void populate() {
		if (null == graph)
			throw new IllegalStateException("Illegal state for 'graph' attribute in Propoli.populate(): the db is not connected");
		try (Transaction tx = graph.beginTx()) {
			Node node_a = graph.createNode(LABEL);
			node_a.setProperty(ID, "a");
			Node node_b = graph.createNode(LABEL);
			node_b.setProperty(ID, "b");
			Node node_c = graph.createNode(LABEL);
			node_c.setProperty(ID, "c");

			Relationship relationship;
			relationship = node_a.createRelationshipTo(node_b, RelTypes.LINKS);
			relationship.setProperty(ID, 2);
			relationship.setProperty(PROB, 0.5);
			relationship = node_b.createRelationshipTo(node_c, RelTypes.LINKS);
			relationship.setProperty(ID, 3);
			relationship.setProperty(PROB, 0.4);
			relationship = node_a.createRelationshipTo(node_c, RelTypes.LINKS);
			relationship.setProperty(ID, 4);
			relationship.setProperty(PROB, 0.5);

			root = node_a;
			done = node_c;
			tx.success();
		}
	}

	public void stamp() {
		if (null != graph && null != root) {
			try (Transaction tx = graph.beginTx()) {

				// Traverser traverser =
				// graph.traversalDescription().relationships(RelTypes.LINKS,
				// Direction.OUTGOING)
				// .evaluator(Evaluators.excludeStartPosition()).traverse(root);
				// for (Path path : traverser) {

				PathFinder<Path> finder = GraphAlgoFactory.allPaths(//
						PathExpanders.forTypeAndDirection(RelTypes.LINKS, Direction.OUTGOING), 4); // 4
																									// is
																									// the
																									// number
																									// of
																									// nodes
																									// +
																									// 1
				Iterable<Path> paths = finder.findAllPaths(root, done);
				for (Path path : paths) {

					boolean first = true;
					for (Relationship relationship : path.relationships()) {
						if (first) {
							System.out.format("(%s)", relationship.getStartNode().getProperty(ID));
							first = false;
						}
						System.out.format(" --[%s:%d,%.2f]-> ", RelTypes.LINKS, relationship.getProperty(ID), relationship.getProperty(PROB, 1.0));
						System.out.format("(%s)", relationship.getEndNode().getProperty(ID));
					}
					System.out.println();
					// System.out.println(Paths.defaultPathToString(path));
				}
				tx.success();
			}
		}
	}

}
