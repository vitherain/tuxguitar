package org.herac.tuxguitar.player.base;

import java.util.ArrayList;
import java.util.List;

import javax.sound.midi.MidiUnavailableException;

import org.apache.log4j.Logger;
import org.herac.tuxguitar.song.managers.TGSongManager;
import org.herac.tuxguitar.song.models.TGChannel;
import org.herac.tuxguitar.song.models.TGDuration;
import org.herac.tuxguitar.song.models.TGMeasureHeader;
import org.herac.tuxguitar.song.models.TGNote;
import org.herac.tuxguitar.song.models.TGString;
import org.herac.tuxguitar.song.models.TGTrack;
import org.herac.tuxguitar.util.TGLock;

public class MidiPlayer {

  /** The Logger for this class. */
  public static final transient Logger LOG = Logger
      .getLogger(MidiPlayer.class);
  
  private static final int MAX_CHANNELS = 16;

  public static final int MAX_VOLUME = 10;

  private static final int TIMER_DELAY = 10;

  private boolean anySolo;

  private boolean changeTickPosition;

  private int infoTrack;

  private List<MidiPlayerListener> listeners;

  protected TGLock lock = new TGLock();

  private int loopEHeader;

  private int loopSHeader;

  private long loopSPosition;

  private boolean metronomeEnabled;

  private int metronomeTrack;

  private MidiPlayerMode mode;

  private MidiOutputPort outputPort;

  private String outputPortKey;

  private List<MidiOutputPortProvider> outputPortProviders;

  private MidiTransmitter outputTransmitter;

  private boolean paused;

  private boolean running;

  private MidiSequencer sequencer;

  private String sequencerKey;

  private List<MidiSequencerProvider> sequencerProviders;

  private TGSongManager songManager;

  protected boolean starting;

  protected long tickLength;

  protected long tickPosition;

  private int volume = MAX_VOLUME;

  public void addListener(MidiPlayerListener listener) {
    if (!this.listeners.contains(listener)) {
      this.listeners.add(listener);
    }
  }

  public void addOutputPortProvider(MidiOutputPortProvider provider)
      throws MidiPlayerException {
    this.addOutputPortProvider(provider, false);
  }

  public void addOutputPortProvider(MidiOutputPortProvider provider,
      boolean tryFirst) throws MidiPlayerException {
    this.outputPortProviders.add(provider);
    this.openOutputPort(provider.listPorts(), tryFirst);
  }

  /**
   * Agrega la Secuencia
   * 
   * 
   */
  public void addSequence() {
    try {
      MidiSequenceParser parser = new MidiSequenceParser(this.songManager,
          MidiSequenceParser.DEFAULT_PLAY_FLAGS, getMode().getCurrentPercent(),
          0);
      MidiSequenceHandler sequence = getSequencer().createSequence(
          this.songManager.getSong().countTracks() + 2);
      parser.setSHeader(getLoopSHeader());
      parser.setEHeader(getLoopEHeader());
      parser.parse(sequence);
      this.infoTrack = parser.getInfoTrack();
      this.metronomeTrack = parser.getMetronomeTrack();
    } catch (MidiPlayerException e) {
      LOG.error(e);
    }
  }

  public void addSequencerProvider(MidiSequencerProvider provider)
      throws MidiPlayerException {
    this.addSequencerProvider(provider, false);
  }

  public void addSequencerProvider(MidiSequencerProvider provider,
      boolean tryFirst) throws MidiPlayerException {
    this.sequencerProviders.add(provider);
    this.openSequencer(provider.listSequencers(), tryFirst);
  }

  private void afterUpdate() {
    try {
      getSequencer().setSolo(this.infoTrack, this.anySolo);
      getSequencer().setSolo(this.metronomeTrack,
          (isMetronomeEnabled() && this.anySolo));
    } catch (MidiPlayerException e) {
      LOG.error(e);
    }
  }

  protected void changeTickPosition() {
    try {
      if (isRunning()) {
        if (this.tickPosition < this.getLoopSPosition()) {
          this.tickPosition = this.getLoopSPosition();
        }
        this.getSequencer().setTickPosition(this.tickPosition);
      }
      setChangeTickPosition(false);
    } catch (MidiPlayerException e) {
      LOG.error(e);
    }
  }

  public void checkDevices() throws Throwable {
    this.getSequencer().check();
    if (this.getOutputPort() != null) {
      this.getOutputPort().check();
    }
  }

  /**
   * Cierra el Secuenciador y Sintetizador
   * 
   * 
   */
  public void close() {
    try {
      this.closeSequencer();
      this.closeOutputPort();
    } catch (MidiPlayerException e) {
      LOG.error(e);
    }
  }

  public void closeOutputPort() {
    try {
      if (this.isRunning()) {
        this.stop();
      }
      this.lock.lock();
      if (this.outputPort != null) {
        this.getOutputTransmitter().removeReceiver(this.outputPort.getKey());
        this.outputPort.close();
        this.outputPort = null;
      }
      this.lock.unlock();
    } catch (Throwable throwable) {
      LOG.error(throwable);
    }
  }

  public void closeSequencer() throws MidiPlayerException {
    try {
      if (this.isRunning()) {
        this.stop();
      }
      this.lock.lock();
      if (this.sequencer != null) {
        this.sequencer.close();
        this.sequencer = null;
      }
      this.lock.unlock();
    } catch (Throwable throwable) {
      throw new MidiPlayerException(throwable.getMessage(), throwable);
    }
  }

  protected void finish() {
    try {
      if (this.getMode().isLoop()) {
        this.setStarting(true);
        this.reset();
        this.getMode().notifyLoop();
        this.notifyLoop();
        this.play();
        return;
      }
      this.reset();
    } catch (MidiPlayerException e) {
      LOG.error(e);
    }
  }

  /**
   * Retorna una lista de instrumentos
   */
  public MidiInstrument[] getInstruments() {
    return MidiInstrument.INSTRUMENT_LIST;
  }

  public int getLoopEHeader() {
    return this.loopEHeader;
  }

  public int getLoopSHeader() {
    return this.loopSHeader;
  }

  public long getLoopSPosition() {
    return this.loopSPosition;
  }

  public MidiPlayerMode getMode() {
    if (this.mode == null) {
      this.mode = new MidiPlayerMode();
    }
    return this.mode;
  }

  /**
   * Retorna el Puerto Midi
   */
  public MidiOutputPort getOutputPort() {
    return this.outputPort;
  }

  public MidiTransmitter getOutputTransmitter() {
    if (this.outputTransmitter == null) {
      this.outputTransmitter = new MidiTransmitter();
    }
    return this.outputTransmitter;
  }

  /**
   * Retorna una lista de instrumentos
   */
  public MidiPercussion[] getPercussions() {
    return MidiPercussion.PERCUSSION_LIST;
  }

  /**
   * Retorna el Sequenciador
   */
  public MidiSequencer getSequencer() {
    if (this.sequencer == null) {
      this.sequencer = new MidiSequencerEmpty();
    }
    return this.sequencer;
  }

  /**
   * Retorna el tick de la nota que esta reproduciendo
   */
  public long getTickPosition() {
    return this.tickPosition;
  }

  public int getVolume() {
    return this.volume;
  }

  /**
   * Inicia el Secuenciador y Sintetizador
   * 
   * 
   */

  public void init(TGSongManager songManager) {
    this.songManager = songManager;
    this.outputPortProviders = new ArrayList<MidiOutputPortProvider>();
    this.sequencerProviders = new ArrayList<MidiSequencerProvider>();
    this.listeners = new ArrayList<MidiPlayerListener>();
    this.getSequencer();
    this.getMode();
    this.reset();
  }

  /**
   * Retorna True si hay cambios en la posicion
   */
  protected boolean isChangeTickPosition() {
    return this.changeTickPosition;
  }

  public boolean isMetronomeEnabled() {
    return this.metronomeEnabled;
  }

  public boolean isOutputPortOpen(String key) {
    if (key != null && getOutputPort() != null) {
      String currentKey = getOutputPort().getKey();
      if (currentKey == null) {
        return false;
      }
      return currentKey.equals(key);
    }
    return false;
  }

  public boolean isPaused() {
    return this.paused;
  }

  /**
   * Retorna True si esta reproduciendo
   */
  public boolean isRunning() {
    try {
      return (this.running || this.getSequencer().isRunning() || this
          .isStarting());
    } catch (MidiPlayerException e) {
      LOG.error(e);
    }
    return false;
  }

  public boolean isSequencerOpen(String key) {
    if (key != null) {
      String currentKey = getSequencer().getKey();
      if (currentKey == null) {
        return false;
      }
      return currentKey.equals(key);
    }
    return false;
  }

  protected boolean isStarting() {
    return this.starting;
  }

  public List<MidiOutputPort> listOutputPorts() {
    List<MidiOutputPort> ports = new ArrayList<MidiOutputPort>();
    for (final MidiOutputPortProvider provider : this.outputPortProviders) {
      try {
        ports.addAll(provider.listPorts());
      } catch (Throwable throwable) {
        LOG.error(throwable);
      }
    }
    return ports;
  }

  public List<MidiSequencer> listSequencers() {
    List<MidiSequencer> sequencers = new ArrayList<MidiSequencer>();
    for (final MidiSequencerProvider provider : this.sequencerProviders) {
      try {
        sequencers.addAll(provider.listSequencers());
      } catch (Throwable throwable) {
        LOG.error(throwable);
      }
    }
    return sequencers;
  }

  public boolean loadOutputPort(MidiOutputPort port) {
    try {
      this.closeOutputPort();
      this.outputPort = port;
      this.outputPort.open();
      this.getOutputTransmitter().addReceiver(this.outputPort.getKey(),
          this.outputPort.getReceiver());
    } catch (Throwable throwable) {
      this.outputPort = null;
      return false;
    }
    return true;
  }

  public boolean loadSequencer(MidiSequencer sequencer) {
    try {
      this.closeSequencer();
      this.sequencer = sequencer;
      this.sequencer.open();
      this.sequencer.setTransmitter(getOutputTransmitter());
    } catch (Throwable throwable) {
      this.sequencer = null;
      return false;
    }
    return true;
  }

  public void notifyLoop() {
    for (final MidiPlayerListener listener : this.listeners) {
      listener.notifyLoop();
    }
  }

  public void notifyStarted() {
    for (final MidiPlayerListener listener : this.listeners) {
      listener.notifyStarted();
    }
  }

  public void notifyStopped() {
    for (final MidiPlayerListener listener : this.listeners) {
      listener.notifyStopped();
    }
  }

  public void openOutputPort(List<MidiOutputPort> ports,
      boolean tryFirst) {
    try {
      if (this.outputPortKey != null
          && !this.isOutputPortOpen(this.outputPortKey)) {
        this.closeOutputPort();
        for (int i = 0; i < ports.size(); i++) {
          MidiOutputPort port = (MidiOutputPort) ports.get(i);
          if (port.getKey().equals(this.outputPortKey)) {
            if (this.loadOutputPort(port)) {
              return;
            }
          }
        }
      }
      if (getOutputPort() == null && !ports.isEmpty() && tryFirst) {
        this.loadOutputPort((MidiOutputPort) ports.get(0));
      }
    } catch (Throwable throwable) {
      LOG.error(throwable);
    }
  }

  public void openOutputPort(String key) {
    this.openOutputPort(key, false);
  }

  public void openOutputPort(String key, boolean tryFirst) {
    this.outputPortKey = key;
    this.openOutputPort(listOutputPorts(), tryFirst);
  }

  public void openSequencer(List<MidiSequencer> sequencers,
      boolean tryFirst) throws MidiPlayerException {
    try {
      if (this.sequencerKey != null && !this.isSequencerOpen(this.sequencerKey)) {
        this.closeSequencer();
        for (int i = 0; i < sequencers.size(); i++) {
          MidiSequencer sequencer = (MidiSequencer) sequencers.get(i);
          if (sequencer.getKey().equals(this.sequencerKey)) {
            if (this.loadSequencer(sequencer)) {
              return;
            }
          }
        }
      }

      if (getSequencer() instanceof MidiSequencerEmpty && !sequencers.isEmpty()
          && tryFirst) {
        this.loadSequencer((MidiSequencer) sequencers.get(0));
      }

    } catch (Throwable throwable) {
      throw new MidiPlayerException(throwable.getMessage(), throwable);
    }
  }

  public void openSequencer(String key) {
    this.openSequencer(key, false);
  }

  public void openSequencer(String key, boolean tryFirst) {
    try {
      this.sequencerKey = key;
      this.openSequencer(listSequencers(), tryFirst);
    } catch (Throwable throwable) {
      LOG.error(throwable);
    }
  }

  public void pause() {
    this.stop(true);
  }

  /**
   * Inicia la reproduccion
   * 
   * @throws MidiPlayerException
   * @throws MidiUnavailableException
   */
  public synchronized void play() throws MidiPlayerException {
    try {
      final boolean notifyStarted = (!this.isRunning());
      this.setStarting(true);
      this.stop();
      this.lock.lock();
      this.checkDevices();
      this.updateLoop(true);
      this.systemReset();
      this.addSequence();
      this.updatePrograms();
      this.updateControllers();
      this.updateDefaultControllers();
      this.setMetronomeEnabled(isMetronomeEnabled());
      this.changeTickPosition();
      this.setRunning(true);
      this.getSequencer().start();
      new Thread(new Runnable() {
        public synchronized void run() {
          try {
            MidiPlayer.this.lock.lock();

            MidiPlayer.this.setStarting(false);

            if (notifyStarted) {
              MidiPlayer.this.notifyStarted();
            }

            MidiPlayer.this.tickLength = getSequencer().getTickLength();
            MidiPlayer.this.tickPosition = getSequencer().getTickPosition();

            Object sequencerLock = new Object();
            while (getSequencer().isRunning() && isRunning()) {
              synchronized (sequencerLock) {
                if (isChangeTickPosition()) {
                  changeTickPosition();
                }
                MidiPlayer.this.tickPosition = getSequencer().getTickPosition();

                sequencerLock.wait(TIMER_DELAY);
              }
            }

            // FINISH
            if (isRunning()) {
              if (MidiPlayer.this.tickPosition >= (MidiPlayer.this.tickLength - (TGDuration.QUARTER_TIME / 2))) {
                finish();
              } else {
                stop(isPaused());
              }
            }

            if (!isRunning()) {
              MidiPlayer.this.notifyStopped();
            }
          } catch (Throwable throwable) {
            setStarting(false);
            reset();
            LOG.error(throwable);
          } finally {
            MidiPlayer.this.lock.unlock();
          }
        }
      }).start();
    } catch (Throwable throwable) {
      this.setStarting(false);
      this.reset();
      throw new MidiPlayerException(throwable.getMessage(), throwable);
    } finally {
      this.lock.unlock();
    }
  }

  public void playBeat(int channel, int program, int volume, int balance,
      int chorus, int reverb, int phaser, int tremolo, int[][] beat) {
    playBeat(channel, program, volume, balance, chorus, reverb, phaser,
        tremolo, beat, 500, 0);
  }

  public void playBeat(int channel, int program, int volume, int balance,
      int chorus, int reverb, int phaser, int tremolo, int[][] beat,
      long duration, int interval) {
    try {
      getOutputTransmitter().sendProgramChange(channel, program);
      getOutputTransmitter().sendControlChange(channel, MidiControllers.VOLUME,
          volume);
      getOutputTransmitter().sendControlChange(channel,
          MidiControllers.BALANCE, balance);
      getOutputTransmitter().sendControlChange(channel, MidiControllers.CHORUS,
          chorus);
      getOutputTransmitter().sendControlChange(channel, MidiControllers.REVERB,
          reverb);
      getOutputTransmitter().sendControlChange(channel, MidiControllers.PHASER,
          phaser);
      getOutputTransmitter().sendControlChange(channel,
          MidiControllers.TREMOLO, tremolo);

      for (int i = 0; i < beat.length; i++) {
        getOutputTransmitter().sendNoteOn(channel, beat[i][0], beat[i][1]);
        if (interval > 0) {
          Thread.sleep(interval);
        }
      }
      Thread.sleep(duration);
      for (int i = 0; i < beat.length; i++) {
        getOutputTransmitter().sendNoteOff(channel, beat[i][0], beat[i][1]);
      }
    } catch (Throwable throwable) {
      LOG.error(throwable);
    }
  }

  public void playBeat(final TGTrack track, final List<TGNote> notes) {
    int channel = track.getChannel().getChannel();
    int program = track.getChannel().getInstrument();
    int volume = (int) ((this.getVolume() / 10.00) * track.getChannel()
        .getVolume());
    int balance = track.getChannel().getBalance();
    int chorus = track.getChannel().getChorus();
    int reverb = track.getChannel().getReverb();
    int phaser = track.getChannel().getPhaser();
    int tremolo = track.getChannel().getTremolo();
    int size = notes.size();
    int[][] beat = new int[size][2];
    for (int i = 0; i < size; i++) {
      TGNote note = (TGNote) notes.get(i);
      beat[i][0] = track.getOffset()
          + (note.getValue() + ((TGString) track.getStrings().get(
              note.getString() - 1)).getValue());
      beat[i][1] = note.getVelocity();
    }
    playBeat(channel, program, volume, balance, chorus, reverb, phaser,
        tremolo, beat);
  }

  public void removeListener(MidiPlayerListener listener) {
    if (this.listeners.contains(listener)) {
      this.listeners.remove(listener);
    }
  }

  public void removeOutputPortProvider(MidiOutputPortProvider provider)
      throws MidiPlayerException {
    this.outputPortProviders.remove(provider);

    MidiOutputPort current = getOutputPort();
    if (current != null) {
      for (final MidiOutputPort port :  provider.listPorts()) {
        if (port.getKey().equals(current.getKey())) {
          closeOutputPort();
          break;
        }
      }
    }
  }

  public void removeSequencerProvider(MidiSequencerProvider provider)
      throws MidiPlayerException {
    this.sequencerProviders.remove(provider);

    MidiSequencer current = getSequencer();
    if (!(current instanceof MidiSequencerEmpty) && current != null) {
      for (final MidiSequencer sequencer : provider.listSequencers()) {
        if (current.getKey().equals(sequencer.getKey())) {
          closeSequencer();
          break;
        }
      }
    }
  }

  /**
   * Resetea los valores
   */
  public void reset() {
    this.stop();
    this.lock.lock();
    this.tickPosition = TGDuration.QUARTER_TIME;
    this.setChangeTickPosition(false);
    this.lock.unlock();
  }

  /**
   * Asigna los cambios de la posicion
   */
  private void setChangeTickPosition(boolean changeTickPosition) {
    this.changeTickPosition = changeTickPosition;
  }

  public void setMetronomeEnabled(boolean metronomeEnabled) {
    try {
      this.metronomeEnabled = metronomeEnabled;
      this.getSequencer().setMute(this.metronomeTrack, !isMetronomeEnabled());
      this.getSequencer().setSolo(this.metronomeTrack,
          (isMetronomeEnabled() && this.anySolo));
    } catch (MidiPlayerException e) {
      LOG.error(e);
    }
  }

  public void setPaused(boolean paused) {
    this.paused = paused;
  }

  /**
   * Asigna el valor a running
   */
  public void setRunning(boolean running) {
    this.running = running;
  }

  protected void setStarting(boolean starting) {
    this.starting = starting;
  }

  /**
   * Indica la posicion del secuenciador
   * 
   * @throws MidiUnavailableException
   */
  public void setTickPosition(long position) {
    this.tickPosition = position;
    this.setChangeTickPosition(true);
  }

  public void setVolume(int volume) {
    this.volume = volume;
    if (this.isRunning()) {
      this.updateControllers();
    }
  }

  /**
   * Para la reproduccion
   * 
   * @throws MidiUnavailableException
   */
  public void stop() {
    this.stop(false);
  }

  /**
   * Para la reproduccion
   * 
   * @throws MidiUnavailableException
   */
  public void stop(boolean paused) {
    try {
      this.setPaused(paused);
      if (this.isRunning()) {
        this.getSequencer().stop();
      }
      this.setRunning(false);
    } catch (MidiPlayerException e) {
      LOG.error(e);
    }
  }

  public void systemReset() {
    try {
      this.getOutputTransmitter().sendSystemReset();
    } catch (MidiPlayerException e) {
      LOG.error(e);
    }
  }

  private void updateController(int channel, int volume, int balance,
      int chorus, int reverb, int phaser, int tremolo, int expression) {
    try {
      getOutputTransmitter().sendControlChange(channel, MidiControllers.VOLUME,
          volume);
      getOutputTransmitter().sendControlChange(channel,
          MidiControllers.BALANCE, balance);
      getOutputTransmitter().sendControlChange(channel, MidiControllers.CHORUS,
          chorus);
      getOutputTransmitter().sendControlChange(channel, MidiControllers.REVERB,
          reverb);
      getOutputTransmitter().sendControlChange(channel, MidiControllers.PHASER,
          phaser);
      getOutputTransmitter().sendControlChange(channel,
          MidiControllers.TREMOLO, tremolo);
      getOutputTransmitter().sendControlChange(channel,
          MidiControllers.EXPRESSION, expression);
    } catch (MidiPlayerException e) {
      LOG.error(e);
    }
  }

  private void updateController(TGTrack track) {
    try {
      int volume = (int) ((this.getVolume() / 10.00) * track.getChannel()
          .getVolume());
      int balance = track.getChannel().getBalance();
      int chorus = track.getChannel().getChorus();
      int reverb = track.getChannel().getReverb();
      int phaser = track.getChannel().getPhaser();
      int tremolo = track.getChannel().getTremolo();

      updateController(track.getChannel().getChannel(), volume, balance,
          chorus, reverb, phaser, tremolo, 127);
      if (track.getChannel().getChannel() != track.getChannel()
          .getEffectChannel()) {
        updateController(track.getChannel().getEffectChannel(), volume,
            balance, chorus, reverb, phaser, tremolo, 127);
      }
      getSequencer().setMute(track.getNumber(), track.isMute());
      getSequencer().setSolo(track.getNumber(), track.isSolo());
    } catch (MidiPlayerException e) {
      LOG.error(e);
    }
  }

  public void updateControllers() {
    this.anySolo = false;
    boolean percussionUpdated = false;
    for (final TGTrack track : this.songManager.getSong().getTracks()) {
      this.updateController(track);
      this.anySolo = ((!this.anySolo) ? track.isSolo() : this.anySolo);
      percussionUpdated = (percussionUpdated || track.isPercussionTrack());
    }
    if (!percussionUpdated && isMetronomeEnabled()) {
      int volume = (int) ((this.getVolume() / 10.00) * TGChannel.DEFAULT_VOLUME);
      int balance = TGChannel.DEFAULT_BALANCE;
      int chorus = TGChannel.DEFAULT_CHORUS;
      int reverb = TGChannel.DEFAULT_REVERB;
      int phaser = TGChannel.DEFAULT_PHASER;
      int tremolo = TGChannel.DEFAULT_TREMOLO;
      updateController(9, volume, balance, chorus, reverb, phaser, tremolo, 127);
    }
    this.afterUpdate();
  }

  private void updateDefaultControllers() {
    try {
      for (int channel = 0; channel < MAX_CHANNELS; channel++) {
        getOutputTransmitter().sendControlChange(channel,
            MidiControllers.RPN_MSB, 0);
        getOutputTransmitter().sendControlChange(channel,
            MidiControllers.RPN_LSB, 0);
        getOutputTransmitter().sendControlChange(channel,
            MidiControllers.DATA_ENTRY_MSB, 12);
        getOutputTransmitter().sendControlChange(channel,
            MidiControllers.DATA_ENTRY_LSB, 0);
      }
    } catch (MidiPlayerException e) {
      LOG.error(e);
    }
  }

  public void updateLoop(boolean force) {
    if (force || !this.isRunning()) {
      this.loopSHeader = -1;
      this.loopEHeader = -1;
      this.loopSPosition = TGDuration.QUARTER_TIME;
      if (getMode().isLoop()) {
        int hCount = this.songManager.getSong().countMeasureHeaders();
        this.loopSHeader = (getMode().getLoopSHeader() <= hCount ? getMode()
            .getLoopSHeader() : -1);
        this.loopEHeader = (getMode().getLoopEHeader() <= hCount ? getMode()
            .getLoopEHeader() : -1);
        if (this.loopSHeader > 0 && this.loopSHeader <= hCount) {
          TGMeasureHeader header = this.songManager
              .getMeasureHeader(this.loopSHeader);
          if (header != null) {
            this.loopSPosition = header.getStart();
          }
        }
      }
    }
  }

  public void updatePrograms() {
    try {
      for (final TGTrack track : this.songManager.getSong().getTracks()) {
        getOutputTransmitter()
            .sendProgramChange(track.getChannel().getChannel(),
                track.getChannel().getInstrument());
        if (track.getChannel().getChannel() != track.getChannel()
            .getEffectChannel()) {
          getOutputTransmitter().sendProgramChange(
              track.getChannel().getEffectChannel(),
              track.getChannel().getInstrument());
        }
      }
    } catch (MidiPlayerException e) {
      LOG.error(e);
    }
  }
}
