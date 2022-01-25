

package sid2;

import javacard.framework.*;

/**
 *
 * @author Hasimbola
 */
public class CompteurApplet extends Applet {
    
 final static byte Wallet_CLA =(byte)0xB0;
     // codes of INS byte in the command APDU header
  final static byte INIT = (byte) 0x10;
  final static byte VERIFY = (byte) 0x20;
  final static byte CREDIT = (byte) 0x30;
  final static byte DEBIT = (byte) 0x40;
  final static byte GET_BALANCE = (byte) 0x50;
  final static byte UNBLOCK = (byte) 0x60;
  final static byte CHANGE_PIN = (byte) 0x70;
  
  
  // maximum balance
  
  final static short MAX_BALANCE = (short) 0x7FFF;
  
  // maximum transaction amount 
  final static short MAX_TRANSACTION_AMOUNT = (short) 0xCB;
  
  
  // maximum number of incorrect tries before the
  // PIN is blocked
  final static byte PIN_TRY_LIMIT =(byte)0x03;
  
  // maximum size PIN
  final static byte MAX_PIN_SIZE =(byte)0x08;
  
  //Minimum PIN size
  final static byte MIN_PIN_SIZE =(byte)0x4;
  
  // signal that the PIN verification failed
  final static short SW_VERIFICATION_FAILED = 0x6312;
   
  // signal the PIN validation is required
  // for a credit or a debit transaction
  final static short SW_PIN_VERIFICATION_REQUIRED = 0x6311;
   
  // signal invalid transaction amount
  // amount > MAX_TRANSACTION_MAOUNT or amount < 0
  final static short SW_INVALID_TRANSACTION_AMOUNT = 0x6A83;
  
  //For change the PIN
  private static final byte TMP_SIZE = (byte)0x08;
   
  // signal that the balance exceed the maximum
  final static short SW_EXCEED_MAXIMUM_BALANCE = 0x6A84;
   
  // signal the balance becomes negative
  final static short SW_NEGATIVE_BALANCE = 0x6A85;
  
//applet state
  static final byte STATE_INITIAL = 1;   // etat avant tout action
  static final byte STATE_PREPERSONALISED = 2; // avant que le pin soit changé
  static final byte STATE_PERSONALISED = 3;  // pin changé; prêt à etre utilise
  static byte state;   // etat avant tout action
  
  //response if PIN is false
  private static final short SW_PIN_INCORRECT_TRIES_LEFT = (short)0x63C0;
  
  /* instance variables declaration */
  OwnerPIN pin;
  OwnerPIN puc = pin;
  short balance;
  byte[] tmp;
  
    public static void install(byte[] bArray, short bOffset, byte bLength) {
        new CompteurApplet(bArray, bOffset, bLength);
         
    }

    /**
     * Only this class's install method should create the applet object.
     */
    protected CompteurApplet(byte[] bArray, short bOffset, byte bLength) {
         // It is good programming practice to allocate
    // all the memory that an applet needs during
    // its lifetime inside the constructor
   
    pin = new OwnerPIN(PIN_TRY_LIMIT, MAX_PIN_SIZE);
	
    state = STATE_INITIAL;
    
    tmp = JCSystem.makeTransientByteArray(TMP_SIZE,  JCSystem.CLEAR_ON_DESELECT);
    
    // The installation parameters contain the PIN
    // initializationvalue 
    
	    byte [] pinArr = {1,2,3,4};
	    pin.update(pinArr, (short) 0, (byte)pinArr.length);
	    register();
    }

    public boolean select() {
    // The applet declines to be selected
    // if the pin is blocked.
    if ( pin.getTriesRemaining() == 0 ) return false;
    return true;
  }// end of select method
   
   public void deselect() {
    // reset the pin value
    pin.reset();
  }
   
    public void process(APDU apdu) {
    	if(selectingApplet()) {
    		return;
    	}
    // APDU object carries a byte array (buffer) to
    // transfer incoming and outgoing APDU header
    // and data bytes between card and CAD
    // At this point, only the first header bytes
    // [CLA, INS, P1, P2, P3] are available in
    // the APDU buffer.
    // The interface javacard.framework.ISO7816
    // declares constants to denote the offset of
    // these bytes in the APDU buffer
    byte[] buffer = apdu.getBuffer();
    
     // check SELECT APDU command
    if ((buffer[ISO7816.OFFSET_CLA] == 0) &&
        (buffer[ISO7816.OFFSET_INS] == (byte)(0xA4))) return;
    
     // verify the reset of commands have the
    // correct CLA byte, which specifies the
    // command structure
    if (buffer[ISO7816.OFFSET_CLA] != Wallet_CLA)
      ISOException.throwIt(ISO7816.SW_CLA_NOT_SUPPORTED);
    
    switch (buffer[ISO7816.OFFSET_INS]) {
      case GET_BALANCE: getBalance(apdu);
        return;
      case DEBIT: debit(apdu);
        return;
      case CREDIT: credit(apdu);
        return;
      case INIT: init(apdu);
        return;
      case VERIFY: verify(apdu);
      	return;
      case CHANGE_PIN: changePin(apdu);
    	  return;
      case UNBLOCK: pin.resetAndUnblock();
        return;
      default: ISOException.throwIt (ISO7816.SW_INS_NOT_SUPPORTED);
    }
  } // end of process method
    
    private void credit(APDU apdu) {
    // access authentication
    if ( ! pin.isValidated()) ISOException.throwIt(SW_PIN_VERIFICATION_REQUIRED);
    byte[] buffer = apdu.getBuffer();
    // Lc byte denotes the number of bytes in the
    // data field of the command APDU
   
    // indicate that this APDU has incoming data
    // and receive data starting at the offset
    // ISO7816.OFFSET_CDATA following the 5 header
    // bytes.
    byte byteRead = (byte)(apdu.setIncomingAndReceive());
   
    // it is an error if the number of data bytes
    // read does not match the number in Lc byte
    if (byteRead != 1) ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
   
    // get the credit amount
    short creditAmount = unsigned(buffer[ISO7816.OFFSET_CDATA]);
   
    // check the credit amount
    if ( ( creditAmount > MAX_TRANSACTION_AMOUNT) || ( creditAmount < 0 ) )
      ISOException.throwIt(SW_INVALID_TRANSACTION_AMOUNT);
   
    // check the new balance
    if ( (byte)( balance + creditAmount) > MAX_BALANCE ) ISOException.throwIt(SW_EXCEED_MAXIMUM_BALANCE);
   
    // credit the amount
    balance = (short)(balance + creditAmount);
  
  } // end of deposit method
    
    private void debit(APDU apdu) {
   
	    // access authentication
	    if ( ! pin.isValidated()) ISOException.throwIt(SW_PIN_VERIFICATION_REQUIRED);
	   
	    byte[] buffer = apdu.getBuffer();
	    byte byteRead = (byte)(apdu.setIncomingAndReceive());
	   
	    if (byteRead != 1) ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
	   
	    // get debit amount
	    short debitAmount = unsigned(buffer[ISO7816.OFFSET_CDATA]);
	   
	    // check debit amount
	    if ( ( debitAmount > MAX_TRANSACTION_AMOUNT) || (debitAmount < 0 ) )
	      ISOException.throwIt(SW_INVALID_TRANSACTION_AMOUNT);
	   
	    // check the new balance
	    if  ( (short)(balance-debitAmount) < 0 ) ISOException.throwIt(SW_NEGATIVE_BALANCE);
	   
	    balance = (short) (balance - debitAmount);
  } // end of debit method
    
    private void init(APDU apdu) {
    	   
    	// access authentication
	    if ( ! pin.isValidated()) ISOException.throwIt(SW_PIN_VERIFICATION_REQUIRED);
	   
	    byte[] buffer = apdu.getBuffer();
	    byte byteRead = (byte)(apdu.setIncomingAndReceive());
	   
	    if (byteRead != 1) ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
	   
	    // get init amount
	    short initAmount = unsigned(buffer[ISO7816.OFFSET_CDATA]);
	   
	    // check init amount
	    if ( ( initAmount > MAX_TRANSACTION_AMOUNT) || (initAmount < 0 ) )
	      ISOException.throwIt(SW_INVALID_TRANSACTION_AMOUNT);
	   
	    // check the new balance
	    if  ( (short)(initAmount) < 0 ) ISOException.throwIt(SW_NEGATIVE_BALANCE);
	   
	    balance = (short) (initAmount);
      } // end of init method
    
    
    private void getBalance(APDU apdu) {
	    if ( ! pin.isValidated()) ISOException.throwIt(SW_PIN_VERIFICATION_REQUIRED);
	    byte[] buffer = apdu.getBuffer();
	    // inform system that the applet has finished
	    // processing the command and the system should
	    // now prepare to construct a response APDU
	    // which contains data field
	    short le = apdu.setOutgoing();
	   
	    if ( le < 2 ) ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
	   
	    //informs the CAD the actual number of bytes
	    //returned
	    apdu.setOutgoingLength((byte)2);
	   
	    // move the balance data into the APDU buffer
	    // starting at the offset 0
	    buffer[0] = (byte)(balance >> 8);
	    buffer[1] = (byte)(balance & 0xFF);
	   
	    // send the 2-balance byte at the offset
	    // 0 in the apdu buffer
	    apdu.sendBytes((short)0, (short)2);
  
  } // end of getBalance method
    
    private void verify(APDU apdu) {
	    byte[] buffer = apdu.getBuffer();
	    // retrieve the PIN data for validation.
	    byte byteRead = (byte)(apdu.setIncomingAndReceive());
	   
	    // check pin
	    // the PIN data is read into the APDU buffer
	    // at the offset ISO7816.OFFSET_CDATA
	    // the PIN data length = byteRead
	    if (! pin.check(buffer, ISO7816.OFFSET_CDATA,byteRead)) {
	    		ISOException.throwIt((short) ((SW_VERIFICATION_FAILED) | pin.getTriesRemaining()));
	    	}
    }
    
    private short unsigned(byte b) {
    	return (short) (b & 0x00FF);
    }
    
    private void changePin(APDU apdu) {
    	 byte[] buf = apdu.getBuffer();
    	 byte byteRead = (byte)(apdu.setIncomingAndReceive());
  	   
 	    // check pin
 	    // the PIN data is read into the APDU buffer
 	    // at the offset ISO7816.OFFSET_CDATA
 	    // the PIN data length = byteRead
 	   pin.update(buf, ISO7816.OFFSET_CDATA,byteRead);
 	   pin.getTriesRemaining();
 	   pin.resetAndUnblock();    
    }
   
    
} // end of class Wallet
  