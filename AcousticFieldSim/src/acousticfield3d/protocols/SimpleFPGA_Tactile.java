package acousticfield3d.protocols;

import acousticfield3d.simulation.Transducer;
import java.util.List;

/**
 * Protocol for FPGA Tactile Modulation firmware
 * 
 * Key differences from SimpleFPGA (Primary):
 * - Uses 16 phase divisions instead of 32 (reduced resolution for tactile feedback)
 * - Supports amplitude modulation step commands (101XXXXX pattern)
 * - No board enable/disable commands (single board operation)
 * - Phase values are automatically adjusted for the firmware's bit shift
 * 
 * @author am14010
 */
public class SimpleFPGA_Tactile extends DeviceConnection{
          
    public byte getStartPhasesCommand(){
        return (byte) (0xFF & 254);
    }
    
    public byte getSwapCommand(){
        return (byte) (0xFF & 253);
    }
    
    public byte getMultiplexCommand(){
        return (byte) (0xFF & 252);
    }
    
    public byte getStartAmplitudesCommand(){
        return (byte) (0xFF & 251);
    }
    
    /**
     * Set amplitude modulation step rate
     * Command format: 101XXXXX where XXXXX is the step value (0-31)
     */
    public byte getAmpModStepCommand(int step){
        return (byte) (0xFF & (0xA0 | (step & 0x1F))); // 0xA0 = 10100000 binary
    }
    
    public int getnTransducers(){
        return 256;
    }
    
    @Override
    public int getDivs() {
        // Tactile firmware uses 16 phase divisions (half of primary)
        // This matches the reduced timing resolution in the FPGA
        return 16;
    }

    @Override
    public int getSpeed() {
        //115200
        //230400 -- FASTEST POSSIBLE FOR MACOS (java limitation rxtx)
        return 230400;
    } 

    @Override
    public void switchBuffers() {
       serial.writeByte( getSwapCommand() );
       serial.flush();
    }
    
    
    @Override
    public void sendPattern(final List<Transducer> transducers) {
       if(serial == null){
            return;
       }
       
       final int nTrans = getnTransducers();
       final byte[] phaseDataPlusHeader = new byte[nTrans + 1];
       final byte[] ampDataPlusHeader = new byte[nTrans + 1];
       final int divs = getDivs();
      
       final byte PHASE_OFF = (byte) (0xFF & getDivs());
       boolean ampModulationNeeded = false;
       
        phaseDataPlusHeader[0] = getStartPhasesCommand(); 
        ampDataPlusHeader[0] = getStartAmplitudesCommand();
        
        for (Transducer t : transducers) {
            final int n = t.getOrderNumber() - number;
            if (n >= 0 && n < nTrans) { //is it within range
                int phase = t.getDiscPhase(divs);
                int amplitude = t.getDiscAmplitude(divs); 
                
                if (t.getpAmplitude() == 0){
                    phase = PHASE_OFF;
                }else if (t.getAmplitude() < 0.99f){
                    ampModulationNeeded = true;
                }
                        
                phaseDataPlusHeader[n+1] = (byte) (phase & 0xFF);
                ampDataPlusHeader[n+1] = (byte) (amplitude & 0xFF);
            }
        }
       serial.write(phaseDataPlusHeader);
       if ( ampModulationNeeded ){
           serial.write(ampDataPlusHeader);
       }
       serial.flush();
    }
    
    /**
     * Set the amplitude modulation step rate
     * @param step Step value from 0-31. Higher values = faster modulation
     */
    public void setAmpModulationStep(int step){
        if(serial == null){
            return;
        }
        serial.writeByte(getAmpModStepCommand(step));
        serial.flush();
    }
    
    @Override
    public void sendToogleQuickMultiplexMode(){
       serial.writeByte(getMultiplexCommand());
       serial.flush();
    }
}

