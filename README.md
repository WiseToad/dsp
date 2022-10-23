Здесь собраны образчики моего кода, надерганные из недавнего проекта, в котором я участвовал.

**Код к сборке не готов, он только для демонстрации**. Где-то чего-то может не хватать. Во многих местах будут нестыковки из-за того, что при выдергивании своего кода из проекта я изменил структуру каталогов и кое-какие файлы.

Комменты в коде могут быть как на русском, так и на английском, потому что во мне периодически просыпается давняя привычка комментить код на буржуйском.

#### Представленный функционал:
1. [Подсистема отчетов](/kotlin/com/groupstp/dsp/reporting)  
Представляет собой настраиваемый и расширяемый движок, или если угодно микро-фреймворк, позволяющий выдавать отчеты в форматах XLSX, HTML и PDF из Excel-шаблонов на основе данных из БД (и не только).  
Для того чтобы сделать новый отчет, нужно нарисовать новый шаблон в виде Excel-файла, и реализовать новый класс Fetcher'а для выборки данных.  
Верстка в PDF была написана с нуля, т.к. имеющиеся библиотеки были либо проприетарны, либо не предоставляли нужного функционала.  
См. также: [Презентация к докладу на внутреннем митапе](/doc/%D0%94%D0%B2%D0%B8%D0%B6%D0%BE%D0%BA%20%D0%BE%D1%82%D1%87%D0%B5%D1%82%D0%BE%D0%B2.odp)

2. ["Умный поиск" на основе Elacticsearch](/kotlin/com/groupstp/dsp/smartsearch)  
В разных местах веб-интерфейса  пользователь имеет возможность ввести строку поиска, тут же в реальном времени ему выдается список результатов, динамически обновляющийся по мере ввода в строку поиска. Поиск ведется в соответствии с бизнес-контекстом формы, откуда ведется поиск.  
Обновление поисковых индексов ведется в режиме, приближенном к риалтайму. Триггерами БД заполняется таблица с очередью изменений, которая постоянно обрабатывается джобом инкрементальной индексации.  
Здесь представлена вторая версия поискового движка, пришедшая на смену более ранней, которую в свое время разрабатывал тоже я.  
См. также: [Описание API для фронтэндеров](/doc/%D0%9F%D0%BE%D0%B8%D1%81%D0%BA%20%D0%BF%D0%BE%20%D1%81%D1%83%D1%89%D0%BD%D0%BE%D1%81%D1%82%D1%8F%D0%BC%202.0%20(SmartSearch).pdf)

3. [Функционал согласования изменений в объектах системы](/kotlin/com/groupstp/dsp/changerequest)  
Пользователь что-то  изменяет в интерфейсе, при этом регистрируется запрос, который передается в смежную систему. Изменения согласовываются, либо отклоняются оператором (возможно с задержкой), после чего ответ возвращается обратно. Обмен происходит через Кафку. Далее, если решение положительное, изменения применяются к экземплярам сущностей.  
По каждому атрибуту решение может быть принято независимо от остальных. Запросы могут включать в себя иерархически связанные между собой сущности.  
Задача решалась максимально обобщенно, с тем чтобы в любой момент можно было расшириться на любые типы сущностей.  
См. также: [Описание формата обмена для смежных разработчиков](/doc/%D0%A1%D0%BE%D0%B3%D0%BB%D0%B0%D1%81%D0%BE%D0%B2%D0%B0%D0%BD%D0%B8%D0%B5%20%D0%B8%D0%B7%D0%BC%D0%B5%D0%BD%D0%B5%D0%BD%D0%B8%D0%B9%2C%20%D1%84%D0%BE%D1%80%D0%BC%D0%B0%D1%82%20%D0%BE%D0%B1%D0%BC%D0%B5%D0%BD%D0%B0.pdf)

Также россыпью представлены кое-какие [утилиты](/kotlin/com/groupstp/dsp/utils) за моим авторством, использующиеся в проекте.
