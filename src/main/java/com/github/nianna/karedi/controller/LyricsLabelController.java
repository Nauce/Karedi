package main.java.com.github.nianna.karedi.controller;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Collector;
import java.util.stream.Collectors;

import javafx.beans.Observable;
import javafx.beans.value.ChangeListener;
import javafx.collections.ListChangeListener;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.layout.Pane;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import main.java.com.github.nianna.karedi.audio.Player.Status;
import main.java.com.github.nianna.karedi.context.AppContext;
import main.java.com.github.nianna.karedi.region.IntBounded;
import main.java.com.github.nianna.karedi.song.Note;
import main.java.com.github.nianna.karedi.song.SongLine;
import main.java.com.github.nianna.karedi.song.SongTrack;
import main.java.com.github.nianna.karedi.util.ListenersUtils;

public class LyricsLabelController implements Controller {
	private static final int VISIBLE_NOTES_LIMIT = 100;
	@FXML
	private Pane pane;
	@FXML
	private TextFlow textFlow;
	@FXML
	private Rectangle clip;

	private Map<Note, NoteText> noteTexts = new HashMap<>();

	private AppContext appContext;
	private IntBounded visibleArea;
	private Note lastColoredNote;

	private ChangeListener<Number> markerBeatChangeListener = this::onMarkerBeatChanged;
	private ListChangeListener<? super Note> noteListChangeListener = createLineListChangeListener();
	private ListChangeListener<? super SongLine> lineListChangeListener = createLineListChangeListener();

	private Collector<Note, ?, Map<SongLine, List<NoteText>>> groupByLine = Collectors.groupingBy(
			Note::getLine, TreeMap::new,
			Collectors.mapping(this::getNoteText, Collectors.toList()));

	@FXML
	public void initialize() {
		clip.heightProperty().bind(pane.heightProperty());
		clip.widthProperty().bind(pane.widthProperty().subtract(50));
	}

	@Override
	public void setAppContext(AppContext appContext) {
		this.appContext = appContext;
		visibleArea = appContext.getVisibleAreaBounds();

		appContext.getVisibleAreaBounds().addListener(this::onVisibleAreaInvalidated);
		appContext.activeTrackProperty().addListener(this::onTrackChanged);
		appContext.activeLineProperty().addListener(this::onLineChanged);
		appContext.getSelected().addListener(
				ListenersUtils.createListContentChangeListener(this::select, this::deselect));
		appContext.playerStatusProperty().addListener(this::onPlayerStatusChanged);
	}

	@Override
	public Node getContent() {
		return pane;
	}

	private void onLineChanged(Observable obs, SongLine oldLine, SongLine newLine) {
		updateLyrics();
	}

	private void onTrackChanged(Observable obs, SongTrack oldTrack, SongTrack newTrack) {
		noteTexts.clear();
		if (oldTrack != null) {
			oldTrack.removeNoteListListener(noteListChangeListener);
			oldTrack.removeLineListListener(lineListChangeListener);
		}
		if (newTrack != null) {
			newTrack.addNoteListListener(noteListChangeListener);
			newTrack.addLineListListener(lineListChangeListener);
		}
		updateLyrics();
	}

	private void onVisibleAreaInvalidated(Observable obs) {
		updateLyrics();
	}

	private <T> ListChangeListener<? super T> createLineListChangeListener() {
		return ListenersUtils.createListChangeListener(e -> updateLyrics(), ListenersUtils::pass,
				e -> updateLyrics(), e -> updateLyrics());
	}

	private void onMarkerBeatChanged(Observable obs, Number oldBeat, Number newBeat) {
		Optional<Note> optionalNote = appContext.getActiveTrack().noteAt(newBeat.intValue());
		if (optionalNote.isPresent()) {
			Note note = optionalNote.get();
			if (note != lastColoredNote) {
				deselect(lastColoredNote);
				select(note);
				lastColoredNote = note;
			}
		} else {
			deselect(lastColoredNote);
			lastColoredNote = null;
		}
	};

	private void onPlayerStatusChanged(Observable obs, Status oldStatus, Status newStatus) {
		if (oldStatus == Status.PLAYING) {
			appContext.markerBeatProperty().removeListener(markerBeatChangeListener);
			if (lastColoredNote != null) {
				deselect(lastColoredNote);
			}
			appContext.getSelected().forEach(this::select);
		}
		if (newStatus == Status.PLAYING) {
			appContext.getSelected().forEach(this::deselect);
			lastColoredNote = null;
			appContext.markerBeatProperty().addListener(markerBeatChangeListener);
		}
	}

	private void updateLyrics() {
		if (appContext.getActiveLine() != null) {
			setActiveLineLyrics();
		} else {
			textFlow.getChildren().clear();
			SongTrack activeTrack = appContext.getActiveTrack();
			if (activeTrack != null) {
				setVisibleNotesLyrics();
			}
		}
	}

	private void setVisibleNotesLyrics() {
		List<Note> visibleNotes = getVisibleNotes();
		if (visibleNotes.size() > VISIBLE_NOTES_LIMIT) {
			textFlow.getChildren().setAll(new Text("(...)"));
		} else {
			visibleNotes.stream().collect(groupByLine).forEach((line, texts) -> {
				Collections.sort(texts);
				textFlow.getChildren().addAll(texts);
				Text lineSeparator = new Text("  / ");
				textFlow.getChildren().add(lineSeparator);
			});

			int size = textFlow.getChildren().size();
			if (size > 0) {
				textFlow.getChildren().remove(size - 1);
			}
		}

	}

	private List<Note> getVisibleNotes() {
		return appContext.getActiveTrack().getNotes(visibleArea.getLowerXBound(),
				visibleArea.getUpperXBound());
	}

	private List<Note> getActiveLineNotes() {
		return appContext.getActiveLine().getNotes();
	}

	private void setActiveLineLyrics() {
		textFlow.getChildren().setAll(
				getActiveLineNotes().stream().map(this::getNoteText).collect(Collectors.toList()));
	}

	private NoteText getNoteText(Note note) {
		NoteText noteText = noteTexts.getOrDefault(note, new NoteText(note));
		noteTexts.put(note, noteText);
		return noteText;
	}

	private void select(Note note) {
		if (noteTexts.containsKey(note)) {
			noteTexts.get(note).activate();
		}
	}

	private void deselect(Note note) {
		if (note != null && noteTexts.containsKey(note)) {
			noteTexts.get(note).deactivate();
		}
	}

	private class NoteText extends Text implements Comparable<NoteText> {
		private static final String ACTIVE_CLASS = "active-lyrics";
		private Note note;
		private boolean activated;

		private NoteText(Note note) {
			this.note = note;
			textProperty().bind(note.lyricsProperty());
		}

		@Override
		public int compareTo(NoteText o) {
			return this.note.compareTo(o.note);
		}

		public void activate() {
			if (!activated) {
				getStyleClass().add(ACTIVE_CLASS);
				activated = true;
			}
		}

		public void deactivate() {
			if (activated) {
				getStyleClass().remove(ACTIVE_CLASS);
				activated = false;
			}
		}
	}
}
