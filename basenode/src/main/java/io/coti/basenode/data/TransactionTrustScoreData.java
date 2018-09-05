package io.coti.basenode.data;

import io.coti.basenode.data.interfaces.ISignValidatable;
import io.coti.basenode.data.interfaces.ISignable;
import lombok.Data;

import javax.validation.constraints.NotNull;
import java.io.Serializable;

@Data
public class TransactionTrustScoreData implements Serializable, ISignable, ISignValidatable {
    @NotNull
    private Hash userHash;
    @NotNull
    private Hash transactionHash;
    @NotNull
    private double trustScore;
    private Hash trustScoreNodeHash;
    private SignatureData trustScoreNodeSignature;

    private TransactionTrustScoreData() {
    }

    public TransactionTrustScoreData(Hash userHash, Hash transactionHash, double trustScore) {
        this.userHash = userHash;
        this.transactionHash = transactionHash;
        this.trustScore = trustScore;
    }

    @Override
    public void setSignerHash(Hash signerHash) {
        trustScoreNodeHash = signerHash;
    }

    @Override
    public void setSignature(SignatureData signature) {
        trustScoreNodeSignature = signature;
    }

    @Override
    public SignatureData getSignature() {
        return trustScoreNodeSignature;
    }

    @Override
    public Hash getSignerHash() {
        return trustScoreNodeHash;
    }
}
