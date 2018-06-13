package io.coti.cotinode.model.Interfaces;

import java.util.Date;
import java.util.List;

public interface ITransaction extends IEntity {
    byte[] getHash();

    boolean isSource();

    ITransaction getLeftParent();

    ITransaction getRightParent();

    void attachToSource(ITransaction source);

    Date getCreateDateTime();

    boolean isThresholdAchieved();

    int getTotalTrustScore();

    int getSenderTrustScore();

    void setTotalTrustScore(int totalTrustScore);

    void setThresholdAchieved(boolean isAchieved);

    void setChildrenTransactions(List<ITransaction> childrenTransactions);
}