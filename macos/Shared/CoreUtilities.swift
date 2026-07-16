import Foundation

enum LocalValue {
    static let calendar: Calendar = { var c=Calendar(identifier:.gregorian);c.locale=Locale(identifier:"en_US_POSIX");c.timeZone=TimeZone(identifier:"Asia/Shanghai")!;return c }()
    static let dateFormatter: DateFormatter = { let f=DateFormatter();f.locale=Locale(identifier:"en_US_POSIX");f.calendar=calendar;f.timeZone=calendar.timeZone;f.dateFormat="yyyy-MM-dd";return f }()
    static func date(_ value:String)->Date?{dateFormatter.date(from:value)}
    static func dateString(_ date:Date)->String{dateFormatter.string(from:date)}
    static func minutes(_ value:String?)->Int?{guard let value,let m=value.firstMatch(#"^\s*([01]?\d|2[0-3])\s*[:：]\s*([0-5]\d)\s*$"#),m.count>=3,let h=Int(m[1]),let min=Int(m[2]) else{return nil};return h*60+min}
    static func timeString(_ minutes:Int)->String{String(format:"%02d:%02d",minutes/60,minutes%60)}
    static func isoNow()->String{ISO8601DateFormatter().string(from:Date())}
    static func instant(_ value:String)->Date{ISO8601DateFormatter().date(from:value) ?? .distantPast}
}
extension String {
    func firstMatch(_ pattern:String,options:NSRegularExpression.Options=[])->[String]?{guard let regex=try?NSRegularExpression(pattern:pattern,options:options)else{return nil};let range=NSRange(startIndex..<endIndex,in:self);guard let result=regex.firstMatch(in:self,range:range)else{return nil};return(0..<result.numberOfRanges).map{idx in let r=result.range(at:idx);guard r.location != NSNotFound,let sr=Range(r,in:self)else{return ""};return String(self[sr])}}
    func allMatches(_ pattern:String,options:NSRegularExpression.Options=[])->[[String]]{guard let regex=try?NSRegularExpression(pattern:pattern,options:options)else{return []};let range=NSRange(startIndex..<endIndex,in:self);return regex.matches(in:self,range:range).map{result in(0..<result.numberOfRanges).map{idx in let r=result.range(at:idx);guard r.location != NSNotFound,let sr=Range(r,in:self)else{return ""};return String(self[sr])}}}
}
enum StableID {
    private static let offset:UInt64=0xcbf29ce484222325,prime:UInt64=0x100000001b3
    static func make(_ prefix:String,_ parts:Any?...)->String{let value=parts.map{String(describing:$0 ?? "").trimmingCharacters(in:.whitespacesAndNewlines)}.joined(separator:"\u{001F}");var hash=offset;for byte in value.utf8{hash ^= UInt64(byte);hash = hash &* prime};return "\(prefix)-\(String(hash,radix:16))"}
    static func colorKey(_ value:String,size:Int=12)->Int{var hash=offset;for byte in value.utf8{hash ^= UInt64(byte);hash = hash &* prime};return Int(hash % UInt64(max(size,1)))}
}
