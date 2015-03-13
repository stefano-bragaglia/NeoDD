/**
 * 
 */
package applet;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.neo4j.graphalgo.GraphAlgoFactory;
import org.neo4j.graphalgo.PathFinder;
import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.DynamicLabel;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Label;
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
public class Problem {

	private int count = 0;

	private final GraphDatabaseService graph;

	private final String path;

	public Problem(String path) {
		if (null == path || (path = path.trim()).isEmpty())
			throw new IllegalArgumentException("Illegal 'path' argument in Problem(String): " + path);
		this.graph = new GraphDatabaseFactory().newEmbeddedDatabase(path);
		Runtime.getRuntime().addShutdownHook(new Thread() {
			@Override
			public void run() {
				graph.shutdown();
			}
		});
		this.path = path;
	}

	public final Relationship add(RelationshipType type, Node tail, Node head, double prob) {
		if (null == type)
			throw new IllegalArgumentException("Illegal 'type' argument in Problem.add(RelationshipType, Node, Node, double): " + type);
		if (null == tail)
			throw new IllegalArgumentException("Illegal 'tail' argument in Problem.add(RelationshipType, Node, Node, double): " + tail);
		if (null == head)
			throw new IllegalArgumentException("Illegal 'head' argument in Problem.add(RelationshipType, Node, Node, double): " + head);
		if (prob < 0.0 || prob > 1.0)
			throw new IllegalArgumentException("Illegal 'prob' argument in Problem.add(RelationshipType, Node, Node, double): " + prob);
		Relationship result = null;
		try (Transaction tx = graph.beginTx()) {
			result = tail.createRelationshipTo(head, type);
			result.setProperty("prob", prob);
			count += 1;
			tx.success();
		}
		return result;
	}

	public final Node add(Map<String, Object> properties, String... types) {
		if (null == properties)
			throw new IllegalArgumentException("Illegal 'properties' argument in Problem.add(Map<String, Object>, String...): " + properties);
		if (null == types)
			throw new IllegalArgumentException("Illegal 'types' argument in Problem.add(Map<String, Object>, String...): " + types);
		Node result = null;
		try (Transaction tx = graph.beginTx()) {
			int i = 0;
			Label[] labels = new Label[types.length];
			for (String type : types) {
				labels[i++] = DynamicLabel.label(type);
			}
			result = graph.createNode(labels);
			for (String property : properties.keySet())
				result.setProperty(property, properties.get(property));
			tx.success();
		}
		return result;
	}

//	public final Node add(String caption) {
//		Node result = null;
//		try (Transaction tx = graph.beginTx()) {
//			result = graph.createNode();
//			if (null == caption || (caption = caption.trim()).isEmpty())
//				result.setProperty("caption", caption);
//			tx.success();
//		}
//		return result;
//	}

	public double correlate(Node source, Node target, RelationshipType type, Direction dir, Object... more) {
		if (null == source)
			throw new IllegalArgumentException("Illegal 'source' argument in Problem.correlate(Node, Node, RelationshipType, Direction, Object...): " + source);
		if (null == target)
			throw new IllegalArgumentException("Illegal 'target' argument in Problem.correlate(Node, Node, RelationshipType, Direction, Object...): " + target);
		if (null == type)
			throw new IllegalArgumentException("Illegal 'type' argument in Problem.correlate(Node, Node, RelationshipType, Direction, Object...): " + type);
		if (null == dir)
			throw new IllegalArgumentException("Illegal 'dir' argument in Problem.correlate(Node, Node, RelationshipType, Direction, Object...): " + dir);
		if (source.equals(target))
			return 1.0;
		try (Transaction ignore = graph.beginTx()) {
			PathExpander<Object> expander = null;
			if (null == more || 0 == more.length)
				expander = PathExpanders.forTypeAndDirection(type, dir);
			else {
				RelationshipType t = (RelationshipType) more[0];
				Direction d = (Direction) more[1];
				expander = PathExpanders.forTypesAndDirections(type, dir, t, d, Arrays.copyOfRange(more, 2, more.length));
			}
			PathFinder<Path> finder = GraphAlgoFactory.allPaths(expander, 1 + count);
			Iterable<Path> paths = finder.findAllPaths(source, target);

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
			bdd.dump("bdd.gv");
			return bdd.traverse();
		}
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

	public final String getPath() {
		return path;
	}

}
