package test.java.com.github.nianna.karedi.parser;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import main.java.com.github.nianna.karedi.parser.element.InvalidSongElementException;
import main.java.com.github.nianna.karedi.parser.element.TrackElement;
import main.java.com.github.nianna.karedi.parser.element.VisitableSongElement;
import main.java.com.github.nianna.karedi.parser.elementparser.TrackParser;

public class TrackParserTest {
	private static TrackParser parser;

	@BeforeClass
	public static void setUpClass() {
		parser = new TrackParser();
	}

	@Test(expected = InvalidSongElementException.class)
	public void doesNotRecognizeWrongInput() throws InvalidSongElementException {
		parser.parse("S 2");
	}

	@Test(expected = InvalidSongElementException.class)
	public void doesNotAllowNegativeTrackNumber() throws InvalidSongElementException {
		parser.parse("P -2");
	}

	@Test
	public void returnsTrackElement() throws InvalidSongElementException {
		VisitableSongElement result = parser.parse("P 2");
		Assert.assertNotNull(result);
		Assert.assertEquals("Object of wrong class returned", TrackElement.class,
				result.getClass());
	}

	@Test
	public void returnsElementWithValidPlayerForCorrectInput() throws InvalidSongElementException {
		TrackElement result = (TrackElement) parser.parse("P 2");
		Assert.assertNotNull(result);
		Assert.assertEquals((Integer) 2, result.getNumber());
	}

}
