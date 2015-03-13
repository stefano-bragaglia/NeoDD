/**
 * 
 */
package applet;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.Set;

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
import org.neo4j.tooling.GlobalGraphOperations;
import org.neo4j.visualization.graphviz.GraphvizWriter;
import org.neo4j.walk.Walker;

/**
 * @author stefano
 *
 */
public class BDD {

	private static enum RelType implements RelationshipType {
		HI_CHILD, LO_CHILD;
	}

	// private static final String DB_PATH = "target/neo4j-side-db";

	private static final String PROB = "prob";

	private static final String REFERENCE = "reference";

	private final GraphDatabaseService graph;

	private final int max;

	private final Node one, root, zero;

	private double value = -1.0;

	public BDD(Set<Set<Relationship>> expression, Set<Relationship> relationships) {
		if (null == relationships)
			throw new IllegalArgumentException("Illegal 'relationships' argument in BDD(Set<Set<Relationship>>, Set<Relationship>): " + relationships);
		if (null == expression)
			throw new IllegalArgumentException("Illegal 'paths' argument in BDD(Set<Set<Relationship>>, Set<Relationship>): " + expression);

		try {
			File db = Files.createTempDirectory("neo4j_bdd").toFile();
			db.deleteOnExit();
			this.graph = new GraphDatabaseFactory().newEmbeddedDatabase(db.toString());
			Runtime.getRuntime().addShutdownHook(new Thread() {
				@Override
				public void run() {
					graph.shutdown();
				}
			});
			try (Transaction tx = graph.beginTx()) {
				for (Relationship relationship : GlobalGraphOperations.at(graph).getAllRelationships())
					relationship.delete();
				for (Node node : GlobalGraphOperations.at(graph).getAllNodes())
					node.delete();
				this.zero = graph.createNode();
				this.zero.setProperty(REFERENCE, "0");
				this.one = graph.createNode();
				this.one.setProperty(REFERENCE, "1");
				this.root = build(expression, relationships);
				this.max = 1 + relationships.size();
				tx.success();
			}
		} catch (IOException e) {
			throw new IllegalArgumentException("Cannot create a temporary Neo4j db for BDDs...");
		}

	}

	private Node build(Set<Set<Relationship>> expression, Set<Relationship> relationships) {
		if (null == expression)
			throw new IllegalArgumentException("Illegal 'expression' argument in BDD.build(Set<Set<Relationship>>, Set<Relationship>): " + expression);
		if (null == relationships)
			throw new IllegalArgumentException("Illegal 'relationships' argument in BDD.build(Set<Set<Relationship>>, Set<Relationship>): " + relationships);

		if (relationships.isEmpty())
			return zero;
		
		Relationship relationship = relationships.iterator().next();

		Set<Relationship> nextLo = new HashSet<>(relationships);
		nextLo.remove(relationship);
		Set<Relationship> nextHi = new HashSet<>(nextLo);

		Set<Set<Relationship>> exprLo = new HashSet<>();
		Set<Set<Relationship>> exprHi = new HashSet<>();
		for (Set<Relationship> path : expression)
			if (path.contains(relationship))
				exprHi.add(path);
			else
				exprLo.add(path);

		Node nodeLo;
		if (exprLo.isEmpty())
			nodeLo = zero;
		else if (nextLo.isEmpty())
			nodeLo = one;
		else
			nodeLo = build(exprLo, nextLo);

		Node nodeHi;
		if (exprHi.isEmpty())
			nodeHi = one;
		else if (nextHi.isEmpty())
			nodeHi = one;
		else
			nodeHi = build(exprHi, nextHi);

		return make(relationship, nodeLo, nodeHi);
	}

	private Node make(Relationship relationship, Node lo, Node hi) {
		Node result = null;
		if (lo.equals(hi))
			return lo;
		try (Transaction tx = graph.beginTx()) {
			long id = relationship.getId();
			Set<Node> nodes = new HashSet<>();
			for (Relationship rel : lo.getRelationships(Direction.INCOMING, RelType.LO_CHILD)) {
				Node node = rel.getStartNode();
				if (node.getProperty(REFERENCE).equals(id))
					nodes.add(node);
			}
			if (!nodes.isEmpty()) {
				for (Relationship rel : hi.getRelationships(Direction.INCOMING, RelType.HI_CHILD)) {
					Node node = rel.getStartNode();
					if (node.getProperty(REFERENCE).equals(id) && nodes.contains(node))
						return node;
				}
			}
			double prob = (double) relationship.getProperty(PROB, 1.0);
			result = graph.createNode();
			result.setProperty(REFERENCE, id);
			result.createRelationshipTo(lo, RelType.LO_CHILD).setProperty(PROB, 1.0 - prob);
			result.createRelationshipTo(hi, RelType.HI_CHILD).setProperty(PROB, prob);
			tx.success();
		}
		return result;
	}

	public void dump(String path) {
		if (null == path || (path = path.trim()).isEmpty())
			throw new IllegalArgumentException("Illegal 'path' argument in Problem.dump(String): " + path);
		try (Transaction ignore = graph.beginTx()) {
			try {
				File file = new File(path);
				OutputStream out = new FileOutputStream(file);
				GraphvizWriter writer = new GraphvizWriter();
				writer.emit(out, Walker.fullGraph(graph));
			} catch (IOException e) {
				throw new IllegalArgumentException("Illegal 'path' argument in Problem.dump(String): " + path);
			}
		}
	}

	public double traverse() {
		if (null != root && value < 0.0) {
			try (Transaction ignore = graph.beginTx()) {
				value = 0.0;
				PathFinder<Path> finder = GraphAlgoFactory.allPaths(
						PathExpanders.forTypesAndDirections(RelType.LO_CHILD, Direction.OUTGOING, RelType.HI_CHILD, Direction.OUTGOING), max);
				Iterable<Path> paths = finder.findAllPaths(root, one);
				for (Path path : paths) {
					double current = 1.0;
					for (Relationship relationship : path.relationships()) {
						current *= (double) relationship.getProperty(PROB, 1.0);
					}
					value += current;
				}
			}
		}
		return value;
	}

}
