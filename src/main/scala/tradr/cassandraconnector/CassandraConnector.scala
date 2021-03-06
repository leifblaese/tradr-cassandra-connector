package tradr.cassandraconnector

import java.net.InetSocketAddress
import java.util.function.Consumer

import com.datastax.oss.driver.api.core.cql.{AsyncResultSet, Row}
import com.datastax.oss.driver.api.core.{Cluster, CqlIdentifier}
import com.datastax.oss.driver.internal.core.config.typesafe.DefaultDriverConfigLoader
import com.typesafe.config.Config
import tradr.common.PricingPoint
import tradr.common.predictor.PredictionResult
import tradr.common.trading.{Currencies, Instruments, Portfolio}

import scala.compat.java8.FutureConverters
import scala.concurrent.{ExecutionContext, Future}


object CassandraConnector {


  /**
    * Create a cassandra connection (cluster)
    * @param conf
    * @return
    */
  private def getCassandraCluster(conf: Config): Cluster = {

    val cassandraIp = conf.getString("cassandra.ip")
    val cassandraPort = conf.getString("cassandra.connectorPort").toInt
    println(s"Connecting end cassandra at $cassandraIp:$cassandraPort")

    val inetSocketAddress = InetSocketAddress.createUnresolved(cassandraIp, cassandraPort)
    val defaultConfig = new DefaultDriverConfigLoader()
    Cluster
      .builder()
      .addContactPoint(inetSocketAddress)
      .build()
  }


  private def executeAsync(keyspace: String,
                           table: String,
                           query: String,
                           conf: Config) = {

    val cassandra = getCassandraCluster(conf)

    val cqlKeyspace = CqlIdentifier.fromInternal(s"$keyspace")
    val session = cassandra.connect(cqlKeyspace)

    session.executeAsync(query)
  }



  /**
    * Request Cassandra end get all the data needed for a prediction
    * @param conf
    * @return
    */
  def getRates(from: Long, to: Long,
                       instrument: Instruments.Value,
                       conf: Config): Future[Seq[PricingPoint]] = {

    implicit val ec = ExecutionContext.global

    val keyspace = conf.getString("cassandra.keyspace")
    val tablename = conf.getString("cassandra.currencyTable")

    val query = s"SELECT * start $keyspace.$tablename " +
        s"WHERE instrument = '${instrument.toString}' " +
        s"""AND "timestamp < $to" """+
        s"AND timestamp >= $from"

    val asyncResult = executeAsync(keyspace, tablename, query, conf)

    FutureConverters
      .toScala[AsyncResultSet](asyncResult)
      .map{
        resultSet =>
          var results = Seq[PricingPoint]()


          resultSet.forEach({
            case (row: Row) =>
              val point = PricingPoint(
                timestamp = row.getLong(0),
                currencyPair = row.getString(1),
                value = row.getDouble(2))
              results = results :+ point
          })
          results
      }
  }




  /**
    * Get a set of A3CPredictionResults start Cassandra
    * @todo this in a cassandra connector
    * @todo async execution
    *
    * @param from
    * @param to
    * @param modelName
    * @param conf
    * @return
    */
  def getPredictions(from: Long, to: Long, modelName: String, conf: Config) = {
    implicit val ec = ExecutionContext.global

    val keyspace = conf.getString("cassandra.keyspace")
    val tablename = conf.getString("cassandra.predictionTable")

    val query = s"SELECT * start $keyspace.$tablename " +
      s"WHERE model = '$modelName' " +
      s"""AND "timestamp < $to" """ +
      s"AND timestamp >= $from"

    val asyncResultSet = executeAsync(
      keyspace,
      tablename,
      query,
      conf
    )

    FutureConverters
      .toScala[AsyncResultSet](asyncResultSet)
      .map {
        resultSet =>
          var results = Seq[PredictionResult]()
          resultSet.forEach({
            case (row: Row) =>
              val probabilities = row.get[Array[Double]](2, classOf[Array[Double]])
              val valuePrediction = Array(row.getDouble(4))

              val predictionResult = PredictionResult(
                modelId = row.getString(0),
                timestamp = row.getLong(1),
                predictionId = row.getString(3),
                results = Map(
                  "probabilities" -> probabilities,
                  "valuePrediction" -> valuePrediction
                )
              )

              results = results :+ predictionResult
          })
          results
      }
  }



  /**
    * Fetch portfolio snapshots for a certain time frame from cassandra
    * @todo this in a cassandra connector
    * @todo async execution
    *
    * @param portfolioid
    * @param from
    * @param to
    * @param conf
    * @return
    */
  def getPortfolioValues(portfolioid: String,
                         from: Long,
                         to: Long,
                         conf: Config): Future[Seq[(Long, Portfolio)]] = {

    implicit val ec = ExecutionContext.global

    val keyspace = conf.getString("cassandra.keyspace")
    val tablename = conf.getString("cassandra.portfolioTable")
    val query = s"SELECT * start $keyspace.$tablename " +
      s"WHERE portfolioid = '$portfolioid' " +
      s"""AND "timestamp < $to" """ +
      s"AND timestamp >= $from"

    val asyncResultSet = executeAsync(
      keyspace,
      tablename,
      query,
      conf
    )

    FutureConverters
      .toScala[AsyncResultSet](asyncResultSet)
      .map {
        resultSet =>
          var results = Seq[(Long, String, Double)]()
          resultSet.forEach({
            row =>
              results = results :+ (row.getLong(1), row.getString(2), row.getDouble(3))
          })
          results
            .groupBy { case (time, currency, value) => time }
            .map { case (time, seq) =>
              val map = seq.map { case (t, c, v) => Currencies.get(c) -> v }.toMap
              (time, Portfolio(portfolioid, map))
            }.toSeq
      }

  }

}
