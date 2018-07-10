import io.coti.fullnode.AppConfig;
import io.coti.common.services.BalanceService;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@ContextConfiguration(classes = AppConfig.class)
public class ConfirmedTransactionServiceTests {

    @Autowired
    private BalanceService balanceService;


//    @Test
//    public void getBalances_EmptyBalancesList_ReturnsEmptyList() {
//        List<BaseTransactionData> balances = balanceService.getBalances(new ArrayList<>());
//        Assert.assertTrue(balances.equals(new ArrayList<>()));
//    }
//
//    @Test
//    public void getBalances_StoreAndRetrieveBalances_ReturnsBalances() {
//        balanceService.addToBalance(new TransactionData(
//                new Hash("TransactionData 1".getBytes())));
//
//        List<BaseTransactionData> balances =
//                balanceService.getBalances(Arrays.asList(
//                        new Hash("Address1".getBytes()),
//                        new Hash("Address2".getBytes())));
//        List<BaseTransactionData> expectedBalances = Arrays.asList(
//                new BaseTransactionData(new Hash("Address1".getBytes()), 12.5),
//                new BaseTransactionData(new Hash("Address2".getBytes()), 44));
//        Assert.assertTrue(balances.equals(expectedBalances));
//    }
}