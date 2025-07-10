package io.hhplus.tdd.point;

import io.hhplus.tdd.database.PointHistoryTable;
import io.hhplus.tdd.database.UserPointTable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/point")
public class PointController {

    private static final Logger log = LoggerFactory.getLogger(PointController.class);

    /**
     * TODO - 특정 유저의 포인트를 조회하는 기능을 작성해주세요.
     */
    @GetMapping("{id}")
    public UserPoint point(
            @PathVariable long id
    ) {
        // 유저 정보 조회
        UserPointTable userPointTable = new UserPointTable();
        UserPoint Result = userPointTable.selectById(id);

        if (Result == null) {
            throw new IllegalArgumentException("해당 유저가 존재하지 않습니다.");
        }
        return Result;
    }


    /**
     * TODO - 특정 유저의 포인트 충전/이용 내역을 조회하는 기능을 작성해주세요.
     */
    @GetMapping("{id}/histories")
    public List<PointHistory> history(
            @PathVariable long id
    ) {
        // 유저 정보 조회
        UserPointTable userPointTable = new UserPointTable();
        UserPoint user = userPointTable.selectById(id);

        if (user == null) {
            throw new IllegalArgumentException("해당 유저가 존재하지 않습니다.");
        }

        // 유저 히스토리 조회
        List<PointHistory> ResultList = new ArrayList<>();
        PointHistoryTable pointHistoryTable = new PointHistoryTable();
        ResultList = pointHistoryTable.selectAllByUserId(id);

        return ResultList;
    }

    /**
     * TODO - 특정 유저의 포인트를 충전하는 기능을 작성해주세요.
     */
    @PatchMapping("{id}/charge")
    public UserPoint charge(
            @PathVariable long id,
            @RequestBody long amount
    ) {
        // 유저 정보 조회
        UserPointTable userPointTable = new UserPointTable();
        UserPoint user = userPointTable.selectById(id);

        if (user == null) {
            throw new IllegalArgumentException("해당 유저가 존재하지 않습니다.");
        }

        // 포인트 충전
        userPointTable.insertOrUpdate(id, user.point() + amount);

        // 히스토리 추가
        PointHistoryTable pointHistoryTable = new PointHistoryTable();
        long currentTime = System.currentTimeMillis();
        pointHistoryTable.insert(id, amount, TransactionType.CHARGE, currentTime);

        // 결과 조회
        UserPoint Result = userPointTable.selectById(id);

        return Result;
    }

    /**
     * TODO - 특정 유저의 포인트를 사용하는 기능을 작성해주세요.
     */
    @PatchMapping("{id}/use")
    public UserPoint use(
            @PathVariable long id,
            @RequestBody long amount
    ) {
        // 유저 정보 조회
        UserPointTable userPointTable = new UserPointTable();
        UserPoint user = userPointTable.selectById(id);

        if (user == null) {
            throw new IllegalArgumentException("해당 유저가 존재하지 않습니다.");
        }

        // 잔여포인트가 적다면 error
        if(user.point() < amount){
            throw new IllegalArgumentException("사용가능한 포인트가 충분하지 않습니다.");
        }

        // 포인트 사용
        userPointTable.insertOrUpdate(id, user.point() - amount);

        // 히스토리 추가
        PointHistoryTable pointHistoryTable = new PointHistoryTable();
        long currentTime = System.currentTimeMillis();
        pointHistoryTable.insert(id, amount, TransactionType.USE, currentTime);

        // 결과 조회
        UserPoint Result = userPointTable.selectById(id);

        return Result;
    }

    @GetMapping("/")
    public String home() {
        return "Hello, this is the home page!";
    }
}
