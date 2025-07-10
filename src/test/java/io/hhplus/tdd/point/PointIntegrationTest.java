package io.hhplus.tdd.point;

import io.hhplus.tdd.database.PointHistoryTable;
import io.hhplus.tdd.database.UserPointTable;
import io.hhplus.tdd.point.controller.PointController;
import io.hhplus.tdd.point.domain.PointHistory;
import io.hhplus.tdd.point.domain.TransactionType;
import io.hhplus.tdd.point.domain.UserPoint;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class PointIntegrationTest {

    @Autowired
    private PointController pointController;

    @Autowired
    private UserPointTable userPointTable;

    @Autowired
    private PointHistoryTable pointHistoryTable;

    @Test
    void 신규_회원_포인트_조회_및_자동생성() {
        long userId = 5000L;

        UserPoint user = pointController.point(userId);
        assertEquals(userId, user.id());
        assertEquals(0L, user.point());

        UserPoint fromDb = userPointTable.selectById(userId);
        assertNotNull(fromDb);
        assertEquals(0L, fromDb.point());
    }

    @Test
    void 포인트_충전_및_사용_통합_검증() {
        long userId = 100L;

        UserPoint user = pointController.point(userId);
        assertEquals(0L, user.point());

        long chargeAmount = 5000L;
        UserPoint chargedUser = pointController.charge(userId, chargeAmount);
        assertEquals(5000L, chargedUser.point());

        long useAmount = 1000L;
        long purchaseAmount = 5000L;
        UserPoint afterUseUser = pointController.use(userId, useAmount, purchaseAmount);
        assertEquals(4000L, afterUseUser.point());

        List<PointHistory> histories = pointController.history(userId);
        assertFalse(histories.isEmpty());
        assertTrue(histories.stream().anyMatch(h -> h.type() == TransactionType.CHARGE));
        assertTrue(histories.stream().anyMatch(h -> h.type() == TransactionType.USE));
    }
}
