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

import static org.eclipse.keyple.card.calypso.DtoAdapters.*;

import java.util.HashMap;
import java.util.Map;
import org.eclipse.keyple.core.util.ApduUtil;
import org.eclipse.keypop.card.ApduResponseApi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Builds the Update Record APDU command.
 *
 * @since 2.0.1
 */
final class CmdCardUpdateRecord extends CardCommand {

  private static final Logger logger = LoggerFactory.getLogger(CmdCardUpdateRecord.class);

  private static final Map<Integer, StatusProperties> STATUS_TABLE;

  static {
    Map<Integer, StatusProperties> m =
        new HashMap<Integer, StatusProperties>(CardCommand.STATUS_TABLE);
    m.put(
        0x6400,
        new StatusProperties(
            "Too many modifications in session", CardSessionBufferOverflowException.class));
    m.put(0x6700, new StatusProperties("Lc value not supported", CardDataAccessException.class));
    m.put(
        0x6981,
        new StatusProperties(
            "Command forbidden on cyclic files when the record exists and is not record 01h and on binary files",
            CardDataAccessException.class));
    m.put(
        0x6982,
        new StatusProperties(
            "Security conditions not fulfilled (no session, wrong key, encryption required)",
            CardSecurityContextException.class));
    m.put(
        0x6985,
        new StatusProperties(
            "Access forbidden (Never access mode, DF is invalidated, etc..)",
            CardAccessForbiddenException.class));
    m.put(
        0x6986,
        new StatusProperties("Command not allowed (no current EF)", CardDataAccessException.class));
    m.put(0x6A82, new StatusProperties("File not found", CardDataAccessException.class));
    m.put(
        0x6A83,
        new StatusProperties(
            "Record is not found (record index is 0 or above NumRec)",
            CardDataAccessException.class));
    m.put(
        0x6B00,
        new StatusProperties("P2 value not supported", CardIllegalParameterException.class));
    STATUS_TABLE = m;
  }

  /* Construction arguments */
  private final int sfi;
  private final int recordNumber;
  private final byte[] data;

  /**
   * Instantiates a new CmdCardUpdateRecord.
   *
   * @param calypsoCard The Calypso card.
   * @param sfi the sfi to select.
   * @param recordNumber the record number to update.
   * @param newRecordData the new record data to write.
   * @throws IllegalArgumentException If record number is &lt; 1
   * @throws IllegalArgumentException If the request is inconsistent
   * @since 2.0.1
   * @deprecated
   */
  @Deprecated
  CmdCardUpdateRecord(
      CalypsoCardAdapter calypsoCard, byte sfi, int recordNumber, byte[] newRecordData) {

    super(CardCommandRef.UPDATE_RECORD, 0, calypsoCard, null, null);

    byte cla = calypsoCard.getCardClass().getValue();
    this.sfi = sfi;
    this.recordNumber = recordNumber;
    this.data = newRecordData;

    byte p2 = (sfi == 0) ? (byte) 0x04 : (byte) ((byte) (sfi * 8) + 4);

    setApduRequest(
        new ApduRequestAdapter(
            ApduUtil.build(
                cla,
                getCommandRef().getInstructionByte(),
                (byte) recordNumber,
                p2,
                newRecordData,
                null)));

    if (logger.isDebugEnabled()) {
      String extraInfo = String.format("SFI:%02Xh, REC:%d", sfi, recordNumber);
      addSubName(extraInfo);
    }
  }

  /**
   * Instantiates a new CmdCardUpdateRecord.
   *
   * @param transactionContext The global transaction context common to all commands.
   * @param commandContext The local command context specific to each command.
   * @param sfi the sfi to select.
   * @param recordNumber the record number to update.
   * @param newRecordData the new record data to write.
   * @throws IllegalArgumentException If record number is &lt; 1
   * @throws IllegalArgumentException If the request is inconsistent
   * @since 2.3.2
   */
  CmdCardUpdateRecord(
      TransactionContextDto transactionContext,
      CommandContextDto commandContext,
      byte sfi,
      int recordNumber,
      byte[] newRecordData) {

    super(CardCommandRef.UPDATE_RECORD, 0, null, transactionContext, commandContext);

    this.sfi = sfi;
    this.recordNumber = recordNumber;
    this.data = newRecordData;

    byte p2 = (sfi == 0) ? (byte) 0x04 : (byte) ((byte) (sfi * 8) + 4);

    setApduRequest(
        new ApduRequestAdapter(
            ApduUtil.build(
                transactionContext.getCard().getCardClass().getValue(),
                getCommandRef().getInstructionByte(),
                (byte) recordNumber,
                p2,
                newRecordData,
                null)));

    if (logger.isDebugEnabled()) {
      String extraInfo = String.format("SFI:%02Xh, REC:%d", sfi, recordNumber);
      addSubName(extraInfo);
    }
  }

  /**
   * {@inheritDoc}
   *
   * @since 2.2.3
   */
  @Override
  void setApduResponseAndCheckStatus(ApduResponseApi apduResponse) throws CardCommandException {
    super.setApduResponseAndCheckStatus(apduResponse);
    getCalypsoCard().setContent((byte) sfi, recordNumber, data);
  }

  /**
   * {@inheritDoc}
   *
   * <p>This command modified the contents of the card and therefore uses the session buffer.
   *
   * @return True
   * @since 2.0.1
   */
  @Override
  boolean isSessionBufferUsed() {
    return true;
  }

  /**
   * {@inheritDoc}
   *
   * @since 2.3.2
   */
  @Override
  void finalizeRequest() {
    encryptRequestAndUpdateTerminalSessionMacIfNeeded();
  }

  /**
   * {@inheritDoc}
   *
   * @since 2.3.2
   */
  @Override
  boolean isCryptoServiceRequiredToFinalizeRequest() {
    return getCommandContext().isEncryptionActive();
  }

  /**
   * {@inheritDoc}
   *
   * @since 2.3.2
   */
  @Override
  boolean synchronizeCryptoServiceBeforeCardProcessing() {
    if (getCommandContext().isEncryptionActive()) {
      return false;
    }
    updateTerminalSessionMacIfNeeded(APDU_RESPONSE_9000);
    return true;
  }

  /**
   * {@inheritDoc}
   *
   * @since 2.3.2
   */
  @Override
  void parseResponse(ApduResponseApi apduResponse) throws CardCommandException {
    decryptResponseAndUpdateTerminalSessionMacIfNeeded(apduResponse);
    super.setApduResponseAndCheckStatus(apduResponse);
    getTransactionContext().getCard().setContent((byte) sfi, recordNumber, data);
    updateTerminalSessionMacIfNeeded();
  }

  /**
   * {@inheritDoc}
   *
   * @since 2.0.1
   */
  @Override
  Map<Integer, StatusProperties> getStatusTable() {
    return STATUS_TABLE;
  }
}
