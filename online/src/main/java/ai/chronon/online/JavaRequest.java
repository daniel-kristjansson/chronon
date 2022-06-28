package ai.chronon.online;

import scala.Option;
import scala.util.ScalaVersionSpecificCollectionsConverter;

import java.util.Map;

public class JavaRequest {
  public String name;
  public Map<String, Object> keys;
  public Long atMillis;

  public JavaRequest(String name, Map<String, Object> keys) {
    this(name, keys, null);
  }

  public JavaRequest(String name, Map<String, Object> keys, Long atMillis) {
    this.name = name;
    this.keys = keys;
    this.atMillis = atMillis;
  }

  public JavaRequest(Fetcher.Request scalaRequest) {
    this.name = scalaRequest.name();
    this.keys = ScalaVersionSpecificCollectionsConverter.convertScalaMapToJava(scalaRequest.keys());
    Option<Object> millisOpt = scalaRequest.atMillis();
    if (millisOpt.isDefined()) {
      this.atMillis = (Long) millisOpt.get();
    }
  }

  public Fetcher.Request toScalaRequest(Boolean isGroupBy) {
    scala.collection.immutable.Map<String, Object> scalaKeys = null;
    if (keys != null) {
      scalaKeys = ScalaVersionSpecificCollectionsConverter.convertJavaMapToScala(keys);
    }
    String gb= null;
    String join= null;
    String method;
    if (isGroupBy) {
      gb = this.name;
      method = "fetchGroupBys";
    } else {
      join = this.name;
      method = "fetchJoin";
    }

    return new Fetcher.Request(
        this.name,
        scalaKeys,
        Option.apply(this.atMillis),
        Option.empty());
  }
}


