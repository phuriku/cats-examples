package com.expedia.cats.examples

import cats.data.EitherT
import cats.{Functor, Monad}
import cats.syntax.either._
import cats.instances.future._

import scala.concurrent.forkjoin.ForkJoinPool
import scala.concurrent.{ExecutionContext, ExecutionContextExecutor, Future}

object Implicits extends App {

  implicit class RichString(str: String) {
    def pet(): Unit = println(s"Petting the string $str. Good string.")
  }

  "Garfield".pet()
}

object ForYield extends App {

  implicit val ec: ExecutionContextExecutor = ExecutionContext.fromExecutor(new ForkJoinPool(1))

  val res0: Future[Int] = for {
    x <- Future(1)
    y <- Future(2)
  } yield x + y

  println(s"res0 = $res0")
}

object Either extends App {

  val res0 = Right[CustomException, Int](3).map(_ + 2)
  // res0: Either[Throwable, Int] = Right(5)

  val res1 = Left[CustomException, Int](new PreconditionException("Something went wrong")).map(_ + 2)
  // res1: Either[Throwable, Int] = Left(PreconditionException)

  val res2 = 3.asRight.map(_ + 2)
  // res2: Either[Throwable, Int] = Right(5)

  val res3 = PreconditionException("Something went wrong").asLeft
  // res3: Either[Throwable, Int] = Left(PreconditionException)

  List(res0, res1, res2, res3).zipWithIndex.foreach(x => println(s"res${x._2}: ${x._1}"))
}

object Map extends App {

  implicit val ec: ExecutionContextExecutor = ExecutionContext.fromExecutor(new ForkJoinPool(1))

  val res0 = List(1, 2, 3).map(n => n + 1)
  // res0: List[Int] = List(2, 3, 4)

  val res1 = Some(1).map(n => n + 1)
  // res1: Option[Int] = Some(2)

  val res2 = Future(1).map(n => n + 1)
  // res2: Future[Int] = Future(2)

  List(res0, res1, res2).zipWithIndex.foreach(x => println(s"res${x._2}: ${x._1}"))
}

object Apply extends App {

  implicit val ec: ExecutionContextExecutor = ExecutionContext.fromExecutor(new ForkJoinPool(1))

  val res0 = List.apply(1)
  // ...or List(1)

  val res1 = Future.apply(1)
  // ...or Future(1)
  // ...or Future.value(1) for Twitter Futures

  val res2 = Option.apply(1)
  // ...or Option(1)

  List(res0, res1, res2).zipWithIndex.foreach(x => println(s"res${x._2}: ${x._1}"))
}

object FlatMap extends App {

  implicit val ec: ExecutionContextExecutor = ExecutionContext.fromExecutor(new ForkJoinPool(1))

  val res0 = List(1, 2, 3).flatMap(n => List(n + 1))
  // res0: List[Int] = List(2, 3, 4)

  val res1 = Some(1).flatMap(n => Some(n + 1))
  // res1: Option[Int] = Some(2)

  val res2 = Future(1).flatMap(n => Future(n + 1))
  // res2: Future[Int] = Future(2)

  List(res0, res1, res2).zipWithIndex.foreach(x => println(s"res${x._2}: ${x._1}"))
}

// TODO: This is missing an implicit import somewhere.
/*
object WriterEx extends App {
  import cats.data.Writer
  import cats.instances.vector._
  import cats.syntax.writer._

  type MyWriter[A] = Writer[Vector[CustomException], A]

  val res0 = for {
    a <- 1.writer(Vector(UnauthorizedException("Unauthorized!")))
    b <- 2.writer(Vector(NotFoundException("Not Found!")))
  } yield a + b
  res0 = Writer(Vector(UnauthorizedException("Unauthorized!"),NotFoundException("Not Found!")), 3)

  println(s"res0 = $res0")
}
*/

object ReaderEx extends App {
  import cats.data.Reader

  case class Cat(name: String)

  val catName: Reader[Cat, String] = Reader(_.name)

  val res0 = catName.run(Cat("Garfield"))
  // res0: String = "Garfield"

  trait CatRepository {
    def findCat(name: String): Cat
    def findFriends(cat: Cat): List[Cat]
  }

  def findCat(name: String): Reader[CatRepository, Cat] = {
    Reader((catRepo: CatRepository) => catRepo.findCat(name))
  }

  def findFriends(cat: Cat): Reader[CatRepository, List[Cat]] = {
    Reader((catRepo: CatRepository) => catRepo.findFriends(cat))
  }

  def findCatFriends(name: String): Reader[CatRepository, List[Cat]] = for {
    cat <- findCat(name)
    friends <- findFriends(cat)
  } yield friends

  val catRepo = new CatRepository {
    def findCat(name: String): Cat = Cat("Garfield")
    def findFriends(cat: Cat): List[Cat] =
      if (cat.name == "Garfield") List(Cat("Cat Friend 1")) else List(Cat("Cat Friend 2"))
  }

  val res1 = findCatFriends("Garfield").run(catRepo)
  // res1: List[Cat] = List(Cat(Cat Friend 1))

  List(res0, res1).zipWithIndex.foreach(x => println(s"res${x._2}: ${x._1}"))
}

object ExceptionHandling extends App {

  class BudgetDao {
    def getBudgetFromDatabase(campaignId: Int): Option[BigDecimal] = ???
    def getBudgetFromDatabaseF(campaignId: Int): Future[Option[BigDecimal]] = ???
  }

  class BudgetService {
    implicit val ec: ExecutionContextExecutor = ExecutionContext.fromExecutor(new ForkJoinPool(1))
    val dao = new BudgetDao

    def getBudget(campaignId: Int): Either[CustomException, BigDecimal] = {
      if (campaignId <= 0) return PreconditionException("Campaign ID must be >0").asLeft

      dao.getBudgetFromDatabase(campaignId) match {
        case Some(budget) => budget.asRight
        case None => NotFoundException(s"The budget for campaign $campaignId wasn't found.").asLeft
      }
    }

    def getBudgetF(campaignId: Int): Future[Either[CustomException, BigDecimal]] = {
      if (campaignId <= 0) return Future(PreconditionException("Campaign ID must be >0").asLeft)

      dao.getBudgetFromDatabaseF(campaignId) map {
        case Some(budget) => budget.asRight
        case None => NotFoundException(s"The budget for campaign $campaignId wasn't found.").asLeft
      }
    }
  }

  class BudgetController {
    case class Request(campaignId: Int)
    val request = Request(campaignId = 1)

    val budgetService = new BudgetService
    budgetService.getBudget(request.campaignId) match {
      case Right(budget) => Ok(budget)
      case Left(ex) => mapExceptionToHttp(ex)
    }

    def mapExceptionToHttp(ex: CustomException): StatusCode = ex match {
      case NotFoundException(_) => NotFound
      case UnauthorizedException(_) => Unauthorized
      case PreconditionException(_) => InternalError
    }
  }
}

object MonadTransform extends App {

  implicit val ec: ExecutionContextExecutor = ExecutionContext.fromExecutor(new ForkJoinPool(1))
  case class Budget(amount: Int)

  def getCampaign(hotelId: Int): Future[Either[CustomException, Int]] = ???
  def getBudget(campaignId: Int): Future[Either[CustomException,Budget]] = ???

  def getBudgetBad(hotelId: Int): Future[Either[CustomException, Budget]] = for {
    campaign <- for {
      c <- getCampaign(hotelId)
    } yield c.getOrElse(-1)
    budget <- getBudget(campaign)
  } yield budget

  def getBudgetGood(hotelId: Int): Future[Either[CustomException,Budget]] = (for {
    campaign <- EitherT(getCampaign(hotelId))
    budget <- EitherT(getBudget(campaign))
  } yield budget).value

  case class FutEither[A](value: Future[Either[CustomException, A]]) {
    def map[B](f: A => B): FutEither[B] = {
      FutEither[B](value.map {_.map(f)})
    }

    def flatMap[B](f: A => FutEither[B]): FutEither[B] = {
      FutEither[B](value.flatMap {
        case Right(x) => f(x).value
        case Left(ex) => Future(Left(ex))
      })
    }
  }

  case class MyEitherT[F[_], A](value: F[Either[CustomException, A]]) {
    def map[B](f: A => B)(implicit F: Functor[F]): MyEitherT[F,B] =
      MyEitherT(F.map(value)(_.map(f)))

    def flatMap[B](f: A => F[B])(implicit F: Monad[F]): MyEitherT[F, B] = {
      MyEitherT(F.flatMap(value) {
        case Right(x) => F.map[B, Either[CustomException,B]](f(x))(_.asRight)
        case Left(ex) => F.pure(Left(ex))
      })
    }
  }
}

sealed trait CustomException
case class PreconditionException(str: String) extends CustomException
case class NotFoundException(str: String) extends CustomException
case class UnauthorizedException(str: String) extends CustomException

sealed trait StatusCode{ val code: Int }
case class Ok(content: Any) extends StatusCode { override val code = 200 }
case object Unauthorized extends StatusCode { override val code = 401 }
case object NotFound extends StatusCode { override val code = 404 }
case object InternalError extends StatusCode { override val code = 500 }