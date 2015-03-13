/**
 * 
 */
package applet;

import java.util.Collections;

import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.RelationshipType;

/**
 * @author stefano
 *
 */
public class Application {

	private static enum RelType implements RelationshipType {
		KNOWS, OWNS;
	}

	private static final String DB_PATH = "target/neo4j-domain-db";

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		// (A) ----------[x2,0.5]----------> (b)
		// ................................. (b) --[x3,0.1]-> (C)
		// (A) --[x4,0.4]-> (d)
		// ................ (d) --[x5,0.5]-> (b)
		// ................................. (b) --[x3,0.1]-> (C)
		// P = 0.06 !!!

		Utils.delete(DB_PATH);

		Problem problem = new Problem(DB_PATH);
		Node a = problem.add(Collections.singletonMap("name", "A"), "Person");
		Node b = problem.add(Collections.singletonMap("name", "B"), "Person");
		Node c = problem.add(Collections.singletonMap("name", "C"), "Dog");
		Node d = problem.add(Collections.singletonMap("name", "D"), "Person");
		Node e = problem.add(Collections.singletonMap("name", "E"), "Dog");
		problem.add(RelType.KNOWS, a, b, 0.5);
		problem.add(RelType.OWNS, b, c, 0.1);
		problem.add(RelType.KNOWS, a, d, 0.4);
		problem.add(RelType.KNOWS, d, b, 0.5);
		problem.add(RelType.OWNS, d, e, 0.7);
		problem.add(RelType.KNOWS, e, c, 0.7);
		problem.dump("problem.gv");

		System.out.format("The probability is %.3f!\n", problem.correlate(a, c, RelType.KNOWS, Direction.OUTGOING));

		System.out.format("The probability is %.3f!\n", problem.correlate(a, c, RelType.KNOWS, Direction.OUTGOING, RelType.OWNS, Direction.OUTGOING));

		System.out.println("Done.");
	}
}
