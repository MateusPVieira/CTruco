package com.cremonezzi.impl.carlsenbot;

import com.bueno.spi.model.CardRank;
import com.bueno.spi.model.CardToPlay;
import com.bueno.spi.model.GameIntel;
import com.bueno.spi.model.TrucoCard;
import com.bueno.spi.service.BotServiceProvider;

import java.util.List;
import java.util.Optional;

/* Notes
 *
 * CARDS RANK ORDER
 *  4 - 5 - 6 - 7 - Q - J - K - A - 2 - 3
 *
 *  40 Cards in the deck (each rank has all the 4 suits => 10 ranks x 4 suits)
 *
 *  CARDS SUIT ORDER
 *  Diamond - Spade - Heart - Club
 *
 * */

public class Carlsen implements BotServiceProvider {
    @Override
    public int getRaiseResponse(GameIntel intel) {
        int qntManilhas = manilhas(intel.getCards(), intel.getVira()).size();
        List<TrucoCard> hand = intel.getCards();

        int highCard = 0;
        int mediumCard = 0;
        for (TrucoCard card : hand) {
            if (isAceOrHigher(card) && !card.isManilha(intel.getVira())) {
                highCard++;
            }
            if(isQueenToKing(card) && !card.isManilha(intel.getVira())){
                mediumCard++;
            }
        }

        if(isAllFours(hand)){
            return 1;
        }

        if (qntManilhas == 0) {
            if(intel.getRoundResults().size() == 0) {
                if (mediumCard >= 1 && highCard == 1) {
                    return 0;
                }
                if (mediumCard == 1 && highCard == 2) {
                    return 1;
                }
            }
            if(calcHandScore(intel.getRoundResults()) == -1){
                if ((mediumCard == 1 && highCard == 1) || mediumCard == 2) {
                    return 0;
                }
                if (highCard == 2) {
                    return 1;
                }
            }
            return -1;
        }

        if (qntManilhas == 1) {
            if (highCard >= 1 || calcHandScore(intel.getRoundResults()) > 0) {
                return 1;
            }

            if(mediumCard >= 1 || calcHandScore(intel.getRoundResults()) >= 0){
                return 0;
            }

            return -1;
        }

        return 1;
    }

    @Override
    public boolean getMaoDeOnzeResponse(GameIntel intel) {
        TrucoCard vira = intel.getVira();

        int lowCardsCount = (int) intel.getCards().stream().filter(trucoCard ->
                        trucoCard.relativeValue(vira) < vira.getRank().value())
                .count();

        return lowCardsCount <= 2;
    }

    @Override
    public boolean decideIfRaises(GameIntel intel) {
        TrucoCard vira = intel.getVira();

        if(isAllFours(intel.getCards()) && intel.getOpponentCard().isEmpty()){
            return true;
        }

        if (intel.getOpponentCard().isPresent() && calcHandScore(intel.getRoundResults()) > 0) {
            TrucoCard opponentCard = intel.getOpponentCard().get();

            return lowestCardToWin(intel.getCards(), opponentCard).isPresent();
        }

        int haveZap = haveZap(intel.getCards(), vira);
        int qntManilhas = manilhas(intel.getCards(), intel.getVira()).size();

        List<TrucoCard> hand = intel.getCards();

        if (calcHandScore(intel.getRoundResults()) > -1) {
            return qntManilhas > 0;
        }

        if (calcHandScore(intel.getRoundResults()) < 0) {
            if (haveZap > -1) {
                for (TrucoCard card : hand) {
                    if (isAceOrHigher(card) && !card.isManilha(intel.getVira())) {
                        return true;
                    }
                }
            }

            return false;
        }

        return qntManilhas > 1;
    }

    @Override
    public CardToPlay chooseCard(GameIntel intel) {
        int handScore = calcHandScore(intel.getRoundResults());

        if (intel.getOpponentCard().isPresent()) {
            TrucoCard opponentCard = intel.getOpponentCard().get();

            if (opponentCard.getRank().equals(CardRank.HIDDEN)) {
                return CardToPlay.of(lowestInHand(intel.getCards(), intel.getVira()));
            }

            Optional<TrucoCard> lowestToWin = lowestCardToWin(intel.getCards(), opponentCard);
            if (lowestToWin.isPresent() && intel.getRoundResults().size() == 1 && handScore < 0) {
                return CardToPlay.of(lowestToWin.get());
            }

            if (handScore > 0) return CardToPlay.discard(lowestInHand(intel.getCards(), intel.getVira()));
        }

        if (handScore < 0) {
            return CardToPlay.of(higherInHand(intel.getCards(), intel.getVira()));
        }

        if (intel.getOpponentCard().isEmpty()) {
            return CardToPlay.of(lowestInHand(intel.getCards(), intel.getVira()));
        }

        TrucoCard opponentCard = intel.getOpponentCard().get();
        Optional<TrucoCard> cardToDraw = hasCardToDraw(intel.getCards(), opponentCard);

        if (handScore == 0 && cardToDraw.isPresent()) {
            return CardToPlay.of(cardToDraw.get());
        }

        if (opponentCard.isZap(intel.getVira())) {
            if (intel.getRoundResults().isEmpty()) {
                return CardToPlay.of(lowestInHand(intel.getCards(), intel.getVira()));
            }

            return CardToPlay.discard(lowestInHand(intel.getCards(), intel.getVira()));
        }

        Optional<TrucoCard> lowestToWin = lowestCardToWin(intel.getCards(), opponentCard);
        if (lowestToWin.isPresent()) {
            return CardToPlay.of(lowestToWin.get());
        }

        TrucoCard lowestInHand = lowestInHand(intel.getCards(), intel.getVira());
        return CardToPlay.of(lowestInHand);
    }

    @Override
    public String getName() {
        return "Trucus Carlsen";
    }

    private int haveZap(List<TrucoCard> botCards, TrucoCard vira) {
        Optional<TrucoCard> haveZap = botCards.stream().filter(trucoCard -> trucoCard.isZap(vira)).findFirst();

        return haveZap.map(botCards::indexOf).orElse(-1);
    }

    private TrucoCard lowestInHand(List<TrucoCard> botCards, TrucoCard vira) {
        TrucoCard lowest = botCards.get(0);

        for (TrucoCard trucoCard : botCards) {
            if (trucoCard.relativeValue(vira) < lowest.relativeValue(vira)) {
                lowest = trucoCard;
            }
        }

        return lowest;
    }

    private TrucoCard higherInHand(List<TrucoCard> botCards, TrucoCard vira) {
        TrucoCard higher = botCards.get(0);

        for (TrucoCard trucoCard : botCards) {
            if (trucoCard.relativeValue(vira) > higher.relativeValue(vira)) {
                higher = trucoCard;
            }
        }

        return higher;
    }

    private List<TrucoCard> manilhas(List<TrucoCard> botCards, TrucoCard vira) {
        return botCards.stream().filter(trucoCard -> trucoCard.isManilha(vira)).toList();
    }

    private boolean isAceOrHigher(TrucoCard card) {
        return card.getRank().equals(CardRank.ACE) || card.getRank().equals(CardRank.TWO) || card.getRank().equals(CardRank.THREE);
    }

    private Optional<TrucoCard> lowestCardToWin(List<TrucoCard> botCards, TrucoCard opponentCard) {
        TrucoCard cardToPlay = null;

        for (TrucoCard cardInHand : botCards) {
            if (cardInHand.relativeValue(opponentCard) > opponentCard.getRank().value()) {
                cardToPlay = cardInHand;
            }
        }

        return Optional.ofNullable(cardToPlay);
    }

    public int calcHandScore(List<GameIntel.RoundResult> roundResults) {
        /*  0  -> TIED
         * -1  -> LOSING
         *  1  -> WINNING
         */

        return roundResults.stream().mapToInt(roundResult -> {
            if (roundResult.equals(GameIntel.RoundResult.WON)) return 1;
            if (roundResult.equals(GameIntel.RoundResult.LOST)) return -1;
            return 0;
        }).sum();
    }

    private boolean isQueenToKing(TrucoCard card) {
        return card.getRank().equals(CardRank.QUEEN) || card.getRank().equals(CardRank.JACK) || card.getRank().equals(CardRank.KING);
    }

    private boolean isAllFours(List<TrucoCard> botCards){
        int qntFours = 0;
        for(TrucoCard card : botCards){
            if(card.getRank().equals(CardRank.FOUR)){
              qntFours++;
            }
        }
        return qntFours == botCards.size();
    }

    private Optional<TrucoCard> hasCardToDraw(List<TrucoCard> botCards, TrucoCard opponentCard){
        TrucoCard cardToPlay = null;

        for(TrucoCard card : botCards){
            if(card.relativeValue(opponentCard) == opponentCard.getRank().value()){
                cardToPlay = card;
            }
        }

        return Optional.ofNullable(cardToPlay);
    }
}
