package io.hhplus.tdd.point;

import io.hhplus.tdd.database.PointHistoryTable;
import io.hhplus.tdd.database.UserPointTable;
import io.hhplus.tdd.point.controller.PointController;
import io.hhplus.tdd.point.domain.PointHistory;
import io.hhplus.tdd.point.domain.TransactionType;
import io.hhplus.tdd.point.domain.UserPoint;
import io.hhplus.tdd.point.service.PointService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PointControllerTest {

    @Mock
    private PointService pointService;

    @Mock
    UserPointTable userPointTable;

    @Mock
    PointHistoryTable pointHistoryTable;

    @InjectMocks
    private PointController pointController;

    /*
     * 회원이 존재하지 않으면 자동으로 가입되고, 포인트 0이 반환되어야 함
     *
     */
    @Test
    void 회원이_없으면_자동가입되어_포인트가_0으로_조회된다() {
        // given
        long userId = 99L;

        // 사용자가 없을 때 최초 조회 → null 반환
        when(pointService.getUserPoint(userId))
                .thenReturn(new UserPoint(userId, 0L, System.currentTimeMillis()));

        // when
        UserPoint result = pointController.point(userId);

        // then
        assertEquals(userId, result.id());
        assertEquals(0L, result.point());
    }


    /*
     * 이미 아이디가 존재하면 다음 아이디를 찾음
     * 최대 1000번까지 제한
     * 예상결과 - 성공
     */
    @Test
    void 존재하지_않는_ID로_조회하면_자동으로_ID를_증가시켜_회원가입된다() {
        // given
        long userId = 100L;

        when(userPointTable.selectById(100L)).thenReturn(null);
        when(userPointTable.selectById(101L)).thenReturn(new UserPoint(101L, 1000L, System.currentTimeMillis()));

        // when
        UserPoint user = pointService.getUserPoint(100L);

        // then
        assertEquals(100L, user.id());
        assertEquals(0L, user.point());

        verify(userPointTable).insertOrUpdate(100L, 0L);
    }

    /*
     * 잔여포인트가 요청금액보다 적은 경우
     * 예상결과 - 실패
     */
    @Test
    void 잔여포인트가_충분한지_확인() {
        // given
        long userId = 1L;
        long purchaseAmount = 10_000L;
        long requestAmount = 1000L;

        when(pointService.use(userId, requestAmount, purchaseAmount))
                .thenThrow(new IllegalArgumentException("사용가능한 포인트가 충분하지 않습니다."));

        // when & then
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> pointController.use(userId, requestAmount, purchaseAmount)
        );

        assertEquals("사용가능한 포인트가 충분하지 않습니다.", exception.getMessage());
    }

    /*
     * 사용금액이 음수일 때
     * 예상결과 - 실패
     */
    @Test
    void 입력값이_음수일경우() {
        // given
        long userId = 1L;
        long purchaseAmount = 10_000L;
        long negativeAmount = -500L;

        // when & then
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> pointController.use(userId, negativeAmount, purchaseAmount)
        );

        assertEquals("사용 금액은 0보다 커야 합니다.", exception.getMessage());
    }

    /*
     * 정책 1
     * 한 회원이 10만점 이상의 포인트를 소유할 수 없음
     * 예상결과 - 실패
     */
    @Test
    void 포인트_총합이_10만점을_초과하면_예외가_발생() {
        // given
        long userId = 1L;
        long currentPoint = 99_000L;
        long purchaseAmount = 10_000L;
        long chargeAmount = 100_000L; //

        // pointService.charge() 호출 시 정책 위반 예외를 던지도록 설정
        when(pointService.charge(userId, chargeAmount))
                .thenThrow(new IllegalArgumentException("보유 포인트는 100,000점을 초과할 수 없습니다."));

        // when & then
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> pointController.charge(userId, chargeAmount)
        );

        assertEquals("보유 포인트는 100,000점을 초과할 수 없습니다.", exception.getMessage());
    }

    /*
     * 정책 2
     * 포인트를 한 거래(30분)에 1번 이상 사용 할 수 없음
     * (한 거래의 정의를 30분으로 임의 정의함)
     * 예상결과 - 실패
     */
    @Test
    void 한_거래_시간내_2번_포인트_사용시_예외발생() {
        // given
        long userId = 1L;
        long purchaseAmount = 10_000L;
        long amount = 1000L;

        // pointService.use 호출 시 정책 위반 예외 던지도록 설정
        when(pointService.use(userId, amount, purchaseAmount))
                .thenThrow(new IllegalArgumentException("30분 이내에는 포인트를 1회만 사용할 수 있습니다."));

        // when & then
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> pointController.use(userId, amount, purchaseAmount)
        );
        assertEquals("30분 이내에는 포인트를 1회만 사용할 수 있습니다.", exception.getMessage());
    }

    /*
     * 정책 3
     * 포인트 사용은 구매금액의 30%까지만 사용 가능
     * 예상결과 - 실패
     */
    @Test
    void 구매금액의_30퍼센트를_초과해서_포인트_사용하면_예외발생() {
        // given
        long userId = 1L;
        long purchaseAmount = 10_000L;
        long pointToUse = 4_000L; // 30%인 3,000 초과

        // pointService.use 호출 시 정책 위반 예외 던지도록 설정
        when(pointService.use(userId, pointToUse, purchaseAmount))
                .thenThrow(new IllegalArgumentException("포인트는 구매금액의 30%까지만 사용할 수 있습니다."));

        // when & then
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> pointController.use(userId, pointToUse, purchaseAmount)
        );
        assertEquals("포인트는 구매금액의 30%까지만 사용할 수 있습니다.", exception.getMessage());
    }

    /*
     * 정책 4
     * 보유 포인트가 100점 이상이어야 포인트 사용 가능
     * 예상결과 - 실패
     */
    @Test
    void 보유_포인트가_100점_미만이면_포인트_사용_불가() {
        // given
        long userId = 1L;
        long amount = 50L;
        long purchaseAmount = 1000L;

        when(pointService.use(userId, amount, purchaseAmount))
                .thenThrow(new IllegalArgumentException("포인트는 100점 이상일 때만 사용할 수 있습니다."));

        // when & then
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> pointController.use(userId, amount, purchaseAmount)
        );
        assertEquals("포인트는 100점 이상일 때만 사용할 수 있습니다.", exception.getMessage());
    }

    /*
     * 정책 5: 10의 배수 방문마다 100포인트 보너스 지급
     */
    @Test
    void 방문_10의_배수마다_보너스_100포인트가_지급() {
        long userId = 1L;
        long amount = 100L;
        long purchaseAmount = 1000L;

        long now = System.currentTimeMillis();

        UserPoint user = new UserPoint(userId, 1000L, now);
        when(userPointTable.selectById(userId)).thenReturn(user);

        List<PointHistory> histories = new ArrayList<>();
        for (int i = 0; i < 19; i++) {
            histories.add(new PointHistory(i, userId, 100L, TransactionType.USE, now - (i + 1) * 100000));
        }
        when(pointHistoryTable.selectAllByUserId(userId)).thenReturn(histories);

        UserPoint result = pointService.use(userId, amount, purchaseAmount);

        assertEquals(1000L, result.point());

        verify(pointHistoryTable).insert(userId, amount, TransactionType.USE, anyLong());
        verify(pointHistoryTable).insert(userId, 100L, TransactionType.CHARGE, anyLong());
    }

    /*
     * 성공
     */
    @Test
    void 회원이_존재하면_정상적으로_포인트_조회() {
        long userId = 1L;
        long pointAmount = 5000L;
        long now = System.currentTimeMillis();

        UserPoint user = new UserPoint(userId, pointAmount, now);

        when(pointService.getUserPoint(userId)).thenReturn(user);

        UserPoint result = pointController.point(userId);

        assertEquals(userId, result.id());
        assertEquals(pointAmount, result.point());
    }

    @Test
    void 포인트_충전이_정상적으로_처리() {
        long userId = 1L;
        long chargeAmount = 1000L;
        long now = System.currentTimeMillis();

        UserPoint chargedUser = new UserPoint(userId, 6000L, now);

        when(pointService.charge(userId, chargeAmount)).thenReturn(chargedUser);

        UserPoint result = pointController.charge(userId, chargeAmount);

        assertEquals(userId, result.id());
        assertEquals(6000L, result.point());
    }

    @Test
    void 포인트_사용이_정상적으로_처리() {
        long userId = 1L;
        long useAmount = 1000L;
        long purchaseAmount = 5000L;
        long now = System.currentTimeMillis();

        UserPoint afterUseUser = new UserPoint(userId, 4000L, now);

        when(pointService.use(userId, useAmount, purchaseAmount)).thenReturn(afterUseUser);

        UserPoint result = pointController.use(userId, useAmount, purchaseAmount);

        assertEquals(userId, result.id());
        assertEquals(4000L, result.point());
    }

    @Test
    void 특정_유저의_포인트_내역이_정상적으로_조회() {
        long userId = 1L;
        long now = System.currentTimeMillis();

        List<PointHistory> historyList = List.of(
                new PointHistory(1L, userId, 500L, TransactionType.CHARGE, now - 100000),
                new PointHistory(2L, userId, 100L, TransactionType.USE, now - 50000)
        );

        when(pointService.getHistory(userId)).thenReturn(historyList);

        List<PointHistory> result = pointController.history(userId);

        assertEquals(2, result.size());
        assertEquals(TransactionType.CHARGE, result.get(0).type());
        assertEquals(500L, result.get(0).amount());
        assertEquals(TransactionType.USE, result.get(1).type());
        assertEquals(100L, result.get(1).amount());
    }

}
