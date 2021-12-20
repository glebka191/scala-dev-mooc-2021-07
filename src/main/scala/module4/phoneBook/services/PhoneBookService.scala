package module4.phoneBook.services

import doobie.quill.DoobieContext
import io.getquill.{CompositeNamingStrategy2, Escape, Literal}
import module4.phoneBook.dao.entities.{Address, PhoneRecord}
import module4.phoneBook.dao.repositories.{AddressRepository, PhoneRecordRepository}
import module4.phoneBook.db.DBTransactor
import module4.phoneBook.dto._
import zio.interop.catz._
import zio.macros.accessible
import zio.random.Random
import zio.{Has, RIO, ZIO, ZLayer}

@accessible
object PhoneBookService {

     type PhoneBookService = Has[Service]
  
     trait Service{
       def find(phone: String): ZIO[DBTransactor, Option[Throwable], (String, PhoneRecordDTO)]
       def insert(phoneRecord: PhoneRecordDTO): RIO[DBTransactor with Random, String]
       def update(id: String, addressId: String, phoneRecord: PhoneRecordDTO): RIO[DBTransactor, Unit]
       def delete(id: String): RIO[DBTransactor, Unit]
     }

    class Impl(phoneRecordRepository: PhoneRecordRepository.Service, addressRepository: AddressRepository.Service) extends Service {
      import doobie.implicits._
       val dc: DoobieContext.Postgres[CompositeNamingStrategy2[Escape.type,Literal.type]] = DBTransactor.doobieContext
       import dc._
        def find(phone: String): ZIO[DBTransactor, Option[Throwable], (String, PhoneRecordDTO)] = for{
          transactor <- DBTransactor.dbTransactor
          result <- phoneRecordRepository.find(phone).transact(transactor).some
        } yield (result.id, PhoneRecordDTO.from(result))

        def insert(phoneRecord: PhoneRecordDTO): RIO[DBTransactor with Random, String] = for{
          transactor <- DBTransactor.dbTransactor
          uuid <- zio.random.nextUUID.map(_.toString())
          uuid2 <- zio.random.nextUUID.map(_.toString())
          address = Address(uuid, phoneRecord.zipCode, phoneRecord.address)
          query = for{
             _ <- addressRepository.insert(address)
             _ <- phoneRecordRepository.insert(PhoneRecord(uuid2, phoneRecord.phone, phoneRecord.fio, address.id))
                  
          } yield ()
         
          _  <-  query.transact(transactor)
        } yield uuid
        
        def update(id: String, addressId: String,  phoneRecord: PhoneRecordDTO): RIO[DBTransactor, Unit] = for{
            transactor <- DBTransactor.dbTransactor
            _ <- phoneRecordRepository.update(PhoneRecord(id, phoneRecord.phone, phoneRecord.fio, addressId)).transact(transactor)
        } yield ()
        
        def delete(id: String): RIO[DBTransactor, Unit] = for{
            transactor <- DBTransactor.dbTransactor
            _ <- phoneRecordRepository.delete(id).transact(transactor)
        } yield ()
        
    }

    val live: ZLayer[PhoneRecordRepository.PhoneRecordRepository with AddressRepository.AddressRepository, Nothing, PhoneBookService.PhoneBookService] = 
      ZLayer.fromServices[PhoneRecordRepository.Service, AddressRepository.Service, PhoneBookService.Service]((repo, addressRepo) => 
        new Impl(repo, addressRepo)
      )

}
