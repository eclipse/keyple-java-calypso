/* **************************************************************************************
 * Copyright (c) 2021 Calypso Networks Association https://www.calypsonet-asso.org/
 *
 * See the NOTICE file(s) distributed with this work for additional information
 * regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License 2.0 which is available at http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 ************************************************************************************** */
package org.eclipse.keyple.calypso;

import java.util.ArrayList;
import java.util.List;
import org.eclipse.keyple.calypso.po.CalypsoPoCardSelection;
import org.eclipse.keyple.calypso.po.CalypsoPoCardSelector;
import org.eclipse.keyple.calypso.po.SelectFileControl;
import org.eclipse.keyple.calypso.transaction.CalypsoDesynchronizedExchangesException;
import org.eclipse.keyple.calypso.transaction.CalypsoPoAnomalyException;
import org.eclipse.keyple.core.card.*;
import org.eclipse.keyple.core.card.spi.CardSelectionSpi;
import org.eclipse.keyple.core.card.spi.SmartCardSpi;
import org.eclipse.keyple.core.service.selection.spi.CardSelector;
import org.eclipse.keyple.core.util.Assert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * (package-private)<br>
 * Implementation of {@link CalypsoPoCardSelection}.
 *
 * @since 2.0
 */
final class CalypsoPoCardSelectionAdapter implements CalypsoPoCardSelection, CardSelectionSpi {

  private static final Logger logger = LoggerFactory.getLogger(CalypsoPoCardSelectionAdapter.class);

  private final List<AbstractPoCommandBuilder<? extends AbstractPoResponseParser>> commandBuilders;
  private final CalypsoPoCardSelector calypsoPoCardSelector;
  private final PoClass poClass;

  /**
   * (package-private)<br>
   * Creates an instance of {@link CalypsoPoCardSelection}.
   *
   * @param calypsoPoCardSelector A card selector.
   * @since 2.0
   * @throws IllegalArgumentException If calypsoPoCardSelector is null.
   */
  CalypsoPoCardSelectionAdapter(CalypsoPoCardSelector calypsoPoCardSelector) {

    Assert.getInstance().notNull(calypsoPoCardSelector, "calypsoPoCardSelector");

    this.calypsoPoCardSelector = calypsoPoCardSelector;
    this.commandBuilders =
        new ArrayList<AbstractPoCommandBuilder<? extends AbstractPoResponseParser>>();
    // deduces the class of the PO according to the type of selection
    if (calypsoPoCardSelector.getAidSelector() == null) {
      poClass = PoClass.LEGACY;
    } else {
      poClass = PoClass.ISO;
    }

    if (logger.isTraceEnabled()) {
      logger.trace("Calypso {} selector", poClass);
    }
  }

  /**
   * {@inheritDoc}
   *
   * @since 2.0
   */
  @Override
  public void prepareReadRecordFile(byte sfi, int recordNumber) {
    commandBuilders.add(CalypsoPoUtils.prepareReadRecordFile(poClass, sfi, recordNumber));
  }

  /**
   * {@inheritDoc}
   *
   * @since 2.0
   */
  @Override
  public void prepareSelectFile(byte[] lid) {
    commandBuilders.add(CalypsoPoUtils.prepareSelectFile(poClass, lid));
  }

  /**
   * {@inheritDoc}
   *
   * @since 2.0
   */
  @Override
  public void prepareSelectFile(short lid) {
    byte[] bLid =
        new byte[] {
          (byte) ((lid >> 8) & 0xff), (byte) (lid & 0xff),
        };
    prepareSelectFile(bLid);
  }

  /**
   * {@inheritDoc}
   *
   * @since 2.0
   */
  @Override
  public void prepareSelectFile(SelectFileControl selectControl) {
    commandBuilders.add(CalypsoPoUtils.prepareSelectFile(poClass, selectControl));
  }

  /**
   * {@inheritDoc}
   *
   * @since 2.0
   */
  @Override
  public CardSelectionRequest getCardSelectionRequest() {
    List<ApduRequest> cardSelectionApduRequests = new ArrayList<ApduRequest>();
    for (AbstractPoCommandBuilder<? extends AbstractPoResponseParser> commandBuilder :
        commandBuilders) {
      cardSelectionApduRequests.add(commandBuilder.getApduRequest());
    }
    // TODO check the boolean use in every creation of CardRequest
    return new CardSelectionRequest(
        calypsoPoCardSelector, new CardRequest(cardSelectionApduRequests, false));
  }

  /**
   * {@inheritDoc}
   *
   * @since 2.0
   */
  @Override
  // TODO check how to handle exceptions in this method
  public SmartCardSpi parse(CardSelectionResponse cardSelectionResponse) {
    List<ApduResponse> apduResponses = cardSelectionResponse.getCardResponse().getApduResponses();

    if (commandBuilders.size() != apduResponses.size()) {
      throw new CalypsoDesynchronizedExchangesException(
          "Mismatch in the number of requests/responses");
    }

    CalypsoPoSmartCardAdapter calypsoPoSmartCard =
        new CalypsoPoSmartCardAdapter(cardSelectionResponse);

    if (!commandBuilders.isEmpty()) {
      try {
        CalypsoPoUtils.updateCalypsoPo(calypsoPoSmartCard, commandBuilders, apduResponses);
      } catch (CalypsoPoCommandException e) {
        throw new CalypsoPoAnomalyException(
            "An error occurred while parsing the card selection request responses", e);
      }
    }

    return calypsoPoSmartCard;
  }

  /**
   * {@inheritDoc}
   *
   * @since 2.0
   */
  @Override
  public CardSelector getCardSelector() {
    return calypsoPoCardSelector;
  }
}
