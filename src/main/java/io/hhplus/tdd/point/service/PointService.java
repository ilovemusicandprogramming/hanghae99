package io.hhplus.tdd.point.service;

import io.hhplus.tdd.database.PointHistoryTable;
import io.hhplus.tdd.database.UserPointTable;
import io.hhplus.tdd.point.domain.PointHistory;
import io.hhplus.tdd.point.domain.TransactionType;
import io.hhplus.tdd.point.domain.UserPoint;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PointService {

    private final UserPointTable userPointTable;
    private final PointHistoryTable pointHistoryTable;

    public PointService(UserPointTable userPointTable, PointHistoryTable pointHistoryTable) {
        this.userPointTable = userPointTable;
        this.pointHistoryTable = pointHistoryTable;
    }

    public UserPoint getUserPoint(long userId) {
        UserPoint user = userPointTable.selectById(userId);

        // 회원이 존재하지 않으면 자동 생성 (ID 중복 회피 정책 적용)
        if (user == null) {
            long candidateId = userId;

            // 최대 1000번 시도
            for (int i = 0; i < 1000; i++) {
                if (userPointTable.selectById(candidateId) == null) {
                    userPointTable.insertOrUpdate(candidateId, 0);
                    return new UserPoint(candidateId, 0, System.currentTimeMillis());
                }
                candidateId++;
            }
            throw new IllegalStateException("사용 가능한 ID를 찾을 수 없습니다.");
        }
        return user;
    }

    public List<PointHistory> getHistory(long userId) {
        getUserPoint(userId); // 존재 확인
        return pointHistoryTable.selectAllByUserId(userId);
    }

    public UserPoint charge(long userId, long amount) {
        UserPoint user = getUserPoint(userId);

        // 정책 1:한 회원이 10만점 이상의 포인트를 소유할 수 없음
        long current = user.point();
        long total = current + amount;

        if(total > 100_000L){
            throw new IllegalArgumentException("보유 포인트는 100,000점을 초과할 수 없습니다.");
        }

        userPointTable.insertOrUpdate(userId, user.point() + amount);
        pointHistoryTable.insert(userId, amount, TransactionType.CHARGE, System.currentTimeMillis());
        return userPointTable.selectById(userId);
    }

    public UserPoint use(long userId, long amount, long purchaseAmount) {
        UserPoint user = getUserPoint(userId);

        if (user.point() < amount) {
            throw new IllegalArgumentException("사용가능한 포인트가 충분하지 않습니다.");
        }

        // 정책 4: 보유 포인트가 100점 이상이어야 함
        if (user.point() < 100) {
            throw new IllegalArgumentException("포인트는 100점 이상일 때만 사용할 수 있습니다.");
        }

        // 정책 2: 포인트를 한 거래(30분)에 1번 이상 사용 할 수 없음
        List<PointHistory> history = pointHistoryTable.selectAllByUserId(userId);
        long now = System.currentTimeMillis();
        long THIRTY_MINUTES = 30 * 60 * 1000;

        boolean usedWithin30Min = history.stream()
                // use인 case만 고르기
                .filter(h -> h.type() == TransactionType.USE)
                // 30분안에 속한게 하나라도 있으면 true
                .anyMatch(h -> now - h.updateMillis() < THIRTY_MINUTES);

        if (usedWithin30Min) {
            throw new IllegalArgumentException("30분 이내에는 포인트를 1회만 사용할 수 있습니다.");
        }

        // 정책 3:포인트 사용은 구매금액의 30%까지만 사용 가능
        long maxUsable = (long) (purchaseAmount * 0.3);
        if (amount > maxUsable) {
            throw new IllegalArgumentException("포인트는 구매금액의 30%까지만 사용할 수 있습니다.");
        }

        // 포인트 사용!!
        userPointTable.insertOrUpdate(userId, user.point() - amount);
        pointHistoryTable.insert(userId, amount, TransactionType.USE, System.currentTimeMillis());

        // 정책 5: 10번째 방문마다 100포인트 보너스 지급
        long visitCount = history.stream()
                .filter(h -> h.type() == TransactionType.USE)
                .count();

        if ((visitCount + 1) % 10 == 0) {
            userPointTable.insertOrUpdate(userId, userPointTable.selectById(userId).point() + 100L);
            pointHistoryTable.insert(userId, 100L, TransactionType.CHARGE, now);
        }

        return userPointTable.selectById(userId);
    }

    public UserPoint createUserIfNotExists(long baseId) {
        long candidateId = baseId;

        // 최대 1000번까지 중복 회피 시도
        for (int i = 0; i < 1000; i++) {
            if (userPointTable.selectById(candidateId) == null) {
                userPointTable.insertOrUpdate(candidateId, 0L);
                return new UserPoint(candidateId, 0L, System.currentTimeMillis());
            }
            candidateId++; // 다음 ID로 증가
        }

        throw new IllegalStateException("사용 가능한 ID를 찾을 수 없습니다.");
    }
}
