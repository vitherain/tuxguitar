/*
 * Created on 16-dic-2005
 * 
 * TODO To change the template for this generated file go to Window -
 * Preferences - Java - Code Style - Code Templates
 */
package org.herac.tuxguitar.io.tg.v08;

import java.awt.Color;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.apache.log4j.Logger;
import org.herac.tuxguitar.gui.editors.tab.TGBeatImpl;
import org.herac.tuxguitar.gui.editors.tab.TGMeasureHeaderImpl;
import org.herac.tuxguitar.gui.editors.tab.TGMeasureImpl;
import org.herac.tuxguitar.gui.editors.tab.TGNoteImpl;
import org.herac.tuxguitar.gui.editors.tab.TGTrackImpl;
import org.herac.tuxguitar.io.base.TGFileFormat;
import org.herac.tuxguitar.io.base.TGFileFormatException;
import org.herac.tuxguitar.io.base.TGInputStreamBase;
import org.herac.tuxguitar.song.models.Clef;
import org.herac.tuxguitar.song.models.TGBeat;
import org.herac.tuxguitar.song.models.TGDivisionType;
import org.herac.tuxguitar.song.models.TGDuration;
import org.herac.tuxguitar.song.models.TGMarker;
import org.herac.tuxguitar.song.models.TGMeasure;
import org.herac.tuxguitar.song.models.TGMeasureHeader;
import org.herac.tuxguitar.song.models.TGNote;
import org.herac.tuxguitar.song.models.TGNoteEffect;
import org.herac.tuxguitar.song.models.TGSong;
import org.herac.tuxguitar.song.models.TGString;
import org.herac.tuxguitar.song.models.TGTempo;
import org.herac.tuxguitar.song.models.TGTimeSignature;
import org.herac.tuxguitar.song.models.TGTrack;
import org.herac.tuxguitar.song.models.TGVoice;
import org.herac.tuxguitar.song.models.effects.BendingEffect;

/**
 * @author julian
 * 
 *         TODO To change the template for this generated type comment go to
 *         Window - Preferences - Java - Code Style - Code Templates
 */
public class TGInputStream extends TGStream implements TGInputStreamBase {

  /** The Logger for this class. */
  public static final transient Logger LOG = Logger
      .getLogger(TGInputStream.class);

  private DataInputStream dataInputStream;
  // private TGFactory factory;
  private String version;

  public TGFileFormat getFileFormat() {
    return new TGFileFormat("TuxGuitar", "*.tg");
  }

  public void init(InputStream stream) {
    this.dataInputStream = new DataInputStream(stream);
    this.version = null;
  }

  public boolean isSupportedVersion() {
    try {
      readVersion();
      return isSupportedVersion(this.version);
    } catch (Throwable throwable) {
      return false;
    }
  }

  public boolean isSupportedVersion(String version) {
    return (version.equals(TG_VERSION));
  }

  private TGSong read() {
    TGSong song = new TGSong();

    // leo el nombre
    song.setName(readString());

    // leo el artista
    song.setArtist(readString());

    // leo el album
    song.setAlbum(readString());

    // leo el autor
    song.setAuthor(readString());

    // leo la cantidad de measure headers
    int headerCount = readShort();

    // leo las pistas
    long headerStart = TGDuration.QUARTER_TIME;
    TGMeasureHeader lastHeader = null;
    for (int i = 0; i < headerCount; i++) {
      TGMeasureHeader header = readMeasureHeader(i + 1, headerStart, lastHeader);
      song.addMeasureHeader(header);
      headerStart += header.getLength();
      lastHeader = header;
    }

    // leo la cantidad de pistas
    int trackCount = readByte();

    // leo las pistas
    for (int i = 0; i < trackCount; i++) {
      song.addTrack(readTrack(i + 1, song));
    }

    return song;
  }

  private BendingEffect readBendEffect() {
    BendingEffect bend = new BendingEffect();

    // leo la cantidad de puntos
    int count = readByte();

    for (int i = 0; i < count; i++) {
      // leo la posicion
      int position = readByte();

      // leo el valor
      int value = readByte();

      // agrego el punto
      bend.addPoint(position, ((value > 0) ? value / 2 : value));
    }
    return bend;
  }

  private int readByte() {
    try {
      return this.dataInputStream.read();
    } catch (IOException e) {
      LOG.error(e);
    }
    return 0;
  }

  private void readChannel(TGTrack track) {
    int header = readHeader();

    // leo el canal
    track.getChannel().setChannel((short) readByte());

    // leo el canal de efectos
    track.getChannel().setEffectChannel((short) readByte());

    // leo el instrumento
    track.getChannel().setInstrument((short) readByte());

    // leo el volumen
    track.getChannel().setVolume((short) readByte());

    // leo el balance
    track.getChannel().setBalance((short) readByte());

    // leo el chorus
    track.getChannel().setChorus((short) readByte());

    // leo el reverb
    track.getChannel().setReverb((short) readByte());

    // leo el phaser
    track.getChannel().setPhaser((short) readByte());

    // leo el tremolo
    track.getChannel().setTremolo((short) readByte());

    // leo el solo
    track.setSolo(((header & CHANNEL_SOLO) != 0));

    // leo el mute
    track.setMute(((header & CHANNEL_MUTE) != 0));
  }

  private Color readColor() {
    final int red = readShort();
    final int green = readShort();
    final int blue = readShort();
    return new Color(red, green, blue);
  }

  private TGBeat readComponent(TGMeasure measure, TGBeat previous) {
    TGBeat beat = previous;

    int header = readHeader();

    // leo el start
    if (beat == null) {
      beat = new TGBeatImpl();
      beat.setStart(measure.getStart());
      measure.addBeat(beat);
    } else if (((header & COMPONENT_NEXT_BEAT) != 0)) {
      beat = new TGBeatImpl();
      beat.setStart(previous.getStart()
          + previous.getVoice(0).getDuration().getTime());
      measure.addBeat(beat);
    }
    TGVoice voice = beat.getVoice(0);
    voice.setEmpty(false);

    // leo la duracion
    if (((header & COMPONENT_NEXT_DURATION) != 0)) {
      readDuration(voice.getDuration());
    } else if (previous != null && !previous.equals(beat)) {
      voice.setDuration(previous.getVoice(0).getDuration().clone());
    }

    if (((header & COMPONENT_NOTE) != 0)) {
      TGNote note = new TGNoteImpl();

      // leo el valor
      note.setValue(readByte());

      // leo el velocity
      note.setVelocity(readByte());

      // leo la cuerda
      note.setString(readByte());

      // leo la ligadura
      note.setTiedNote(((header & COMPONENT_TIEDNOTE) != 0));

      // leo los efectos
      if (((header & COMPONENT_EFFECT) != 0)) {
        readNoteEffect(note.getEffect());
      }

      voice.addNote(note);
    }
    return beat;
  }

  private TGDivisionType readDivisionType() {
    // leo los enters
    final int enters = readByte();

    // leo los tiempos
    final int times = readByte();
    
    return new TGDivisionType(enters, times);
  }

  private void readDuration(TGDuration duration) {
    int header = readHeader();

    duration.setDotted((header & DURATION_DOTTED) != 0);

    duration.setDoubleDotted((header & DURATION_DOUBLE_DOTTED) != 0);

    // leo el valor
    duration.setValue(readByte());

    // leo el tipo de divisiones
    if (((header & DURATION_TUPLETO) != 0)) {
      duration.setDivision(this.readDivisionType());
    }
  }

  private int readHeader() {
    try {
      return this.dataInputStream.read();
    } catch (IOException e) {
      LOG.error(e);
    }
    return 0;
  }

  private TGString readInstrumentString(int number) {
    return new TGString(number, readByte());
  }

  private TGMarker readMarker(int measure) {
    TGMarker marker = new TGMarker();

    // leo el compas
    marker.setMeasure(measure);

    // leo el titulo
    marker.setTitle(readString());

    // leo el color
    marker.setColor(readColor());

    return marker;
  }

  private TGMeasure readMeasure(TGMeasureHeader measureHeader,
      TGMeasure lastMeasure) {
    int header = readHeader();

    TGMeasure measure = new TGMeasureImpl(measureHeader);

    // leo la cantidad de componentes
    TGBeat previous = null;
    int componentCount = readShort();
    for (int i = 0; i < componentCount; i++) {
      previous = readComponent(measure, previous);
    }

    // leo la clave
    measure
        .setClef((lastMeasure == null) ? Clef.TREBLE : lastMeasure.getClef());
    if (((header & MEASURE_CLEF) != 0)) {
      final int clefCode = readByte();

      Clef clef = null;
      switch (clefCode) {
      case 1:
        clef = Clef.TREBLE;
        break;
      case 2:
        clef = Clef.BASS;
        break;
      case 3:
        clef = Clef.TENOR;
        break;
      case 4:
        clef = Clef.ALTO;
        break;
      }

      measure.setClef(clef);
    }

    // leo el key signature
    measure.setKeySignature((lastMeasure == null) ? 0 : lastMeasure
        .getKeySignature());
    if (((header & MEASURE_KEYSIGNATURE) != 0)) {
      measure.setKeySignature(readByte());
    }

    return measure;
  }

  private TGMeasureHeader readMeasureHeader(int number, long start,
      TGMeasureHeader lastMeasureHeader) {
    int header = readHeader();

    TGMeasureHeader measureHeader = new TGMeasureHeaderImpl();
    measureHeader.setNumber(number);
    measureHeader.setStart(start);

    // leo el time signature
    if (((header & MEASURE_HEADER_TIMESIGNATURE) != 0)) {
      readTimeSignature(measureHeader.getTimeSignature());
    } else if (lastMeasureHeader != null) {
      measureHeader.setTimeSignature(lastMeasureHeader.getTimeSignature().clone());
    }

    // leo el tempo
    if (((header & MEASURE_HEADER_TEMPO) != 0)) {
      readTempo(measureHeader.getTempo());
    } else if (lastMeasureHeader != null) {
      measureHeader.setTempo(lastMeasureHeader.getTempo().clone());
    }

    // leo el comienzo de la repeticion
    measureHeader.setRepeatOpen(((header & MEASURE_HEADER_OPEN_REPEAT) != 0));

    // leo el numero de repeticiones
    if (((header & MEASURE_HEADER_CLOSE_REPEAT) != 0)) {
      measureHeader.setRepeatClose(readShort());
    }

    // leo el marker
    if (((header & MEASURE_HEADER_MARKER) != 0)) {
      measureHeader.setMarker(readMarker(number));
    }

    measureHeader
        .setTripletFeel(((lastMeasureHeader != null) ? lastMeasureHeader
            .getTripletFeel() : TGMeasureHeader.TRIPLET_FEEL_NONE));
    if (((header & MEASURE_HEADER_TRIPLET_FEEL) != 0)) {
      measureHeader.setTripletFeel(readByte());
    }
    return measureHeader;
  }

  private void readNoteEffect(TGNoteEffect effect) {
    int header = readHeader();

    // leo el vibrato
    effect.setVibrato(((header & EFFECT_VIBRATO) != 0));

    // leo la nota muerta
    effect.setDeadNote(((header & EFFECT_DEAD_NOTE) != 0));

    // leo el slide
    effect.setSlide(((header & EFFECT_SLIDE) != 0));

    // leo el hammer
    effect.setHammer(((header & EFFECT_HAMMER) != 0));

    // leo el bend
    if (((header & EFFECT_BEND) != 0)) {
      effect.setBend(readBendEffect());
    }
  }

  private short readShort() {
    try {
      return this.dataInputStream.readShort();
    } catch (IOException e) {
      LOG.error(e);
    }
    return 0;
  }

  public TGSong readSong() throws TGFileFormatException {
    try {
      if (this.isSupportedVersion()) {
        TGSong song = this.read();
        this.dataInputStream.close();
        return song;
      }
      throw new TGFileFormatException("Unsopported Version");
    } catch (Throwable throwable) {
      throw new TGFileFormatException(throwable);
    }
  }

  private String readString() {
    try {
      int length = this.dataInputStream.read();
      char[] chars = new char[length];
      for (int i = 0; i < chars.length; i++) {
        chars[i] = this.dataInputStream.readChar();
      }
      return String.copyValueOf(chars);
    } catch (IOException e) {
      LOG.error(e);
    }
    return null;
  }

  private void readTempo(TGTempo tempo) {
    // leo el valor
    tempo.setValue(readShort());
  }

  private void readTimeSignature(TGTimeSignature timeSignature) {
    // leo el numerador
    timeSignature.setNumerator(readByte());

    // leo el denominador
    readDuration(timeSignature.getDenominator());
  }

  private TGTrack readTrack(int number, TGSong song) {
    TGTrack track = new TGTrackImpl();

    track.setNumber(number);

    // leo el nombre
    track.setName(readString());

    // leo el canal
    readChannel(track);

    // leo la cantidad de compases
    int measureCount = song.countMeasureHeaders();

    // leo los compases
    TGMeasure lastMeasure = null;
    for (int i = 0; i < measureCount; i++) {
      TGMeasure measure = readMeasure(song.getMeasureHeader(i), lastMeasure);
      track.addMeasure(measure);
      lastMeasure = measure;
    }

    // leo la cantidad de cuerdas
    int stringCount = readByte();

    // leo las cuerdas
    for (int i = 0; i < stringCount; i++) {
      track.getStrings().add(readInstrumentString(i + 1));
    }

    // leo el offset
    track.setOffset((TGTrack.MIN_OFFSET + readByte()));

    // leo el color
    track.setColor(readColor());

    return track;
  }

  private void readVersion() {
    if (this.version == null) {
      this.version = readString();
    }
  }
}
