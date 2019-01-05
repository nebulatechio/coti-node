package io.coti.financialserver.services;

import io.coti.basenode.http.Response;
import io.coti.basenode.http.interfaces.IResponse;
import io.coti.financialserver.crypto.DisputeItemVoteCrypto;
import io.coti.financialserver.crypto.DisputeUpdateItemCrypto;
import io.coti.financialserver.data.*;
import io.coti.financialserver.http.UpdateItemRequest;
import io.coti.financialserver.http.VoteRequest;
import io.coti.financialserver.model.Disputes;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import static io.coti.financialserver.http.HttpStringConstants.*;

@Slf4j
@Service
public class ItemService {

    @Autowired
    Disputes disputes;
    @Autowired
    DisputeUpdateItemCrypto disputeUpdateItemCrypto;
    @Autowired
    DisputeItemVoteCrypto disputeItemVoteCrypto;
    @Autowired
    DisputeService disputeService;

    public ResponseEntity<IResponse> updateItem(UpdateItemRequest request) {

        DisputeUpdateItemData disputeUpdateItemData = request.getDisputeUpdateItemData();

        if (!disputeUpdateItemCrypto.verifySignature(disputeUpdateItemData)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new Response(INVALID_SIGNATURE, STATUS_ERROR));
        }

        DisputeData disputeData = disputes.getByHash(disputeUpdateItemData.getDisputeHash());

        if (disputeData == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new Response(DISPUTE_NOT_FOUND, STATUS_ERROR));
        }

        ActionSide actionSide;
        if (disputeData.getConsumerHash().equals(disputeUpdateItemData.getUserHash())) {
            actionSide = ActionSide.Consumer;
        } else if (disputeData.getMerchantHash().equals(disputeUpdateItemData.getUserHash())) {
            actionSide = ActionSide.Merchant;
        } else {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new Response(UNAUTHORIZED, STATUS_ERROR));
        }

        for (Long itemId : disputeUpdateItemData.getItemIds()) {
            try {
                DisputeItemStatusService.valueOf(disputeUpdateItemData.getStatus().toString()).changeStatus(disputeData, itemId, actionSide);
            } catch (Exception e) {
                e.printStackTrace();
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new Response(e.getMessage(), STATUS_ERROR));
            }
        }
        disputeService.update(disputeData);

        return ResponseEntity.status(HttpStatus.OK).body(new Response(DISPUTE_ITEM_UPDATE_SUCCESS, STATUS_SUCCESS));
    }

    public ResponseEntity<IResponse> vote(VoteRequest request) {

        DisputeItemVoteData disputeItemVoteData = request.getDisputeItemVoteData();
        
        if (!disputeItemVoteCrypto.verifySignature(disputeItemVoteData)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new Response(INVALID_SIGNATURE, STATUS_ERROR));
        }

        DisputeData disputeData = disputes.getByHash(disputeItemVoteData.getDisputeHash());

        if (disputeData == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new Response(DISPUTE_NOT_FOUND, STATUS_ERROR));
        }

        if (!disputeData.getArbitratorHashes().contains(disputeItemVoteData.getArbitratorHash())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new Response(UNAUTHORIZED, STATUS_ERROR));
        }

        if (disputeData.getDisputeStatus() != DisputeStatus.Claim) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new Response(DISPUTE_NOT_IN_CLAIM_STATUS, STATUS_ERROR));
        }

        DisputeItemData disputeItemData = disputeData.getDisputeItem(disputeItemVoteData.getItemId());

        if (disputeItemData == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new Response(ITEM_NOT_FOUND, STATUS_ERROR));
        }

        if (disputeItemData.arbitratorAlreadyVoted(disputeItemVoteData.getArbitratorHash())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new Response(ALREADY_GOT_YOUR_VOTE, STATUS_ERROR));
        }

        disputeItemData.addItemVoteData(disputeItemVoteData);

        try {
            disputeService.updateAfterVote(disputeData, disputeItemData);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new Response(e.getMessage(), STATUS_ERROR));
        }

        return ResponseEntity.status(HttpStatus.OK).body(new Response(DISPUTE_ITEM_VOTE_SUCCESS, STATUS_SUCCESS));
    }
}
