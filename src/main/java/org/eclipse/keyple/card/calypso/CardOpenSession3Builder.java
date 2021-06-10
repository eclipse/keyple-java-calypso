/* **************************************************************************************
 * Copyright (c) 2018 Calypso Networks Association https://calypsonet.org/
 *
 * See the NOTICE file(s) distributed with this work for additional information
 * regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License 2.0 which is available at http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 ************************************************************************************** */
package org.eclipse.keyple.card.calypso;

import org.calypsonet.terminal.calypso.card.CalypsoCard;
import org.calypsonet.terminal.card.ApduResponseApi;
import org.eclipse.keyple.core.util.ApduUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * (package-private)<br>
 * Builds the Open Session command for a card revision 3.X.
 *
 * @since 2.0
 */
final class CardOpenSession3Builder
    extends AbstractCardOpenSessionBuilder<AbstractCardOpenSessionParser> {

  private static final Logger logger = LoggerFactory.getLogger(CardOpenSession3Builder.class);

  // Construction arguments used for parsing
  private final int sfi;
  private final int recordNumber;

  /**
   * Instantiates a new AbstractCardOpenSessionBuilder.
   *
   * @param calypsoCard The {@link CalypsoCard}.
   * @param keyIndex the key index.
   * @param samChallenge the sam challenge returned by the SAM Get Challenge APDU command.
   * @param sfi the sfi to select.
   * @param recordNumber the record number to read.
   * @throws IllegalArgumentException - if the request is inconsistent
   * @since 2.0
   */
  public CardOpenSession3Builder(
      CalypsoCard calypsoCard, byte keyIndex, byte[] samChallenge, int sfi, int recordNumber) {
    super(calypsoCard);

    this.sfi = sfi;
    this.recordNumber = recordNumber;

    byte p1 = (byte) ((recordNumber * 8) + keyIndex);
    byte p2;
    byte[] dataIn;

    if (!calypsoCard.isExtendedModeSupported()) {
      p2 = (byte) ((sfi * 8) + 1);
      dataIn = samChallenge;
    } else {
      p2 = (byte) ((sfi * 8) + 2);
      dataIn = new byte[samChallenge.length + 1];
      dataIn[0] = (byte) 0x00;
      System.arraycopy(samChallenge, 0, dataIn, 1, samChallenge.length);
    }

    /*
     * case 4: this command contains incoming and outgoing data. We define le = 0, the actual
     * length will be processed by the lower layers.
     */
    byte le = 0;

    setApduRequest(
        new ApduRequestAdapter(
            ApduUtil.build(
                CalypsoCardClass.ISO.getValue(),
                CalypsoCardCommand.getOpenSessionForRev(calypsoCard).getInstructionByte(),
                p1,
                p2,
                dataIn,
                le)));

    if (logger.isDebugEnabled()) {
      String extraInfo =
          String.format("KEYINDEX=%d, SFI=%02X, REC=%d", keyIndex, sfi, recordNumber);
      this.addSubName(extraInfo);
    }
  }

  /**
   * {@inheritDoc}
   *
   * @since 2.0
   */
  @Override
  public CardOpenSession3Parser createResponseParser(ApduResponseApi apduResponse) {
    return new CardOpenSession3Parser(apduResponse, this);
  }

  /**
   * {@inheritDoc}
   *
   * <p>This command can't be executed in session and therefore doesn't uses the session buffer.
   *
   * @return false
   * @since 2.0
   */
  @Override
  public boolean isSessionBufferUsed() {
    return false;
  }

  /**
   * {@inheritDoc}
   *
   * @since 2.0
   */
  @Override
  public int getSfi() {
    return sfi;
  }

  /**
   * {@inheritDoc}
   *
   * @since 2.0
   */
  @Override
  public int getRecordNumber() {
    return recordNumber;
  }
}
