package logbook.api;

import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import javax.json.JsonObject;

import logbook.bean.AppCondition;
import logbook.bean.AppConfig;
import logbook.bean.BattleLog;
import logbook.bean.BattleResult;
import logbook.bean.BattleTypes.IFormation;
import logbook.bean.Ship;
import logbook.bean.ShipCollection;
import logbook.internal.BattleLogs;
import logbook.internal.Logs;
import logbook.internal.PhaseState;
import logbook.internal.log.BattleResultLogFormat;
import logbook.internal.log.LogWriter;
import logbook.proxy.RequestMetaData;
import logbook.proxy.ResponseMetaData;

/**
 * /kcsapi/api_req_sortie/battleresult
 *
 */
@API("/kcsapi/api_req_sortie/battleresult")
public class ApiReqSortieBattleresult implements APIListenerSpi {

    @Override
    public void accept(JsonObject json, RequestMetaData req, ResponseMetaData res) {
        JsonObject data = json.getJsonObject("api_data");
        if (data != null) {

            BattleLog log = AppCondition.get().getBattleResult();
            if (log != null) {
                // 削除
                AppCondition.get().setBattleResult(null);

                AppCondition.get().setBattleResultConfirm(log);

                log.setResult(BattleResult.toBattleResult(data));
                // ローデータを設定する
                if (AppConfig.get().isIncludeRawData()) {
                    BattleLog.setRawData(log, BattleLog.RawData::setResult, data, req);
                }
                log.setTime(Logs.nowString());
                // 出撃艦隊
                Integer dockId = Optional.ofNullable(log.getBattle())
                        .map(IFormation::getDockId)
                        .orElse(1);
                // 艦隊スナップショットを作る
                BattleLog.snapshot(log, dockId);
                // 戦闘ログの保存
                BattleLogs.write(log);

                LogWriter.getInstance(BattleResultLogFormat::new)
                        .write(log);
                if (AppConfig.get().isApplyResult()) {
                    // 艦隊を更新
                    PhaseState p = new PhaseState(log);
                    p.apply(log.getBattle());
                    p.apply(log.getMidnight());
                    ShipCollection.get()
                            .getShipMap()
                            .putAll(p.getAfterFriend().stream()
                                    .filter(Objects::nonNull)
                                    .collect(Collectors.toMap(Ship::getId, v -> v)));
                }
            }
        }
    }
}
